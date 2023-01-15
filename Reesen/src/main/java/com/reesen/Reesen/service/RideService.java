package com.reesen.Reesen.service;

import com.reesen.Reesen.Enums.RideStatus;
import com.reesen.Reesen.Enums.Role;
import com.reesen.Reesen.Enums.TypeOfReport;
import com.reesen.Reesen.Enums.VehicleName;
import com.reesen.Reesen.dto.*;
import com.reesen.Reesen.model.*;
import com.reesen.Reesen.model.Driver.Driver;
import com.reesen.Reesen.repository.*;
import com.reesen.Reesen.service.interfaces.ILocationService;
import com.reesen.Reesen.service.interfaces.IWorkingHoursService;
import org.springframework.beans.factory.annotation.Autowired;
import com.reesen.Reesen.service.interfaces.IRideService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RideService implements IRideService {
	
    private final RideRepository rideRepository;
	private final RouteRepository routeRepository;
	private final PassengerRepository passengerRepository;
	private final VehicleTypeRepository vehicleTypeRepository;
	private final PanicRepository panicRepository;
	private final UserRepository userRepository;
	private final DriverRepository driverRepository;
	private final IWorkingHoursService workingHoursService;
	private final ILocationService locationService;
	private final DeductionRepository deductionRepository;
	private final ReviewRepository reviewRepository;
	private final ScheduledExecutorService executor;
	private PassengerService passengerService;

	@Autowired
    public RideService(RideRepository rideRepository, RouteRepository routeRepository, PassengerRepository passengerRepository, VehicleTypeRepository vehicleTypeRepository, PanicRepository panicRepository, UserRepository userRepository, DriverRepository driverRepository, IWorkingHoursService workingHoursService, ILocationService locationService, DeductionRepository deductionRepository, ReviewRepository reviewRepository, PassengerService passengerService){
        this.rideRepository = rideRepository;
		this.routeRepository = routeRepository;
		this.passengerRepository = passengerRepository;
		this.vehicleTypeRepository = vehicleTypeRepository;
		this.panicRepository = panicRepository;
		this.userRepository = userRepository;
		this.driverRepository = driverRepository;
		this.workingHoursService = workingHoursService;
		this.locationService = locationService;
		this.deductionRepository = deductionRepository;
		this.reviewRepository = reviewRepository;
		this.passengerService = passengerService;
		this.executor = Executors.newScheduledThreadPool(1);
	}


	@Override
	public Ride findOne(Long id) {
		Ride ride = this.rideRepository.findById(id).get();
		if(ride != null)
		{
			ride.setDriver(this.driverRepository.findDriverByRidesContaining(ride).get());
			ride.setPassengers(this.passengerService.findPassengersByRidesContaining(ride));
			ride.setLocations(this.rideRepository.getLocationsByRide(ride.getId()));
		}
		return ride;
	}

	@Override
	public Ride save(Ride ride) {
		return this.rideRepository.save(ride);
	}

	@Override
	public RideDTO createRideDTO(CreateRideDTO rideDTO) {
		Ride ride = new Ride();
		Set<RouteDTO> locationsDTOs = rideDTO.getLocations();
		LinkedHashSet<Route> locations = new LinkedHashSet<>();
		for(RouteDTO routeDTO: locationsDTOs){
			locations.add(this.routeRepository.findById(routeDTO.getId()).get());
		}
		
		ride.setLocations(locations);
		ride.setVehicleType(this.vehicleTypeRepository.findByName(VehicleName.valueOf(rideDTO.getVehicleType())));
		ride.setBabyAccessible(rideDTO.isBabyTransport());
		ride.setPetAccessible(rideDTO.isPetTransport());
		Set<UserDTO> passengersDTOs = rideDTO.getPassengers();
		Set<Passenger> passengers = new HashSet<>();
		for(UserDTO userDTO: passengersDTOs){
			passengers.add(this.passengerRepository.findByEmail(userDTO.getEmail()));
		}
		ride.setPassengers(passengers);
		ride.setStatus(RideStatus.ON_HOLD);

		if(ride.getScheduledTime() != null){
			Runnable task = () -> {
				Object[] result = this.findSuitableDriver(ride);
				if (result[0] == null) {
					ride.setStatus(RideStatus.REJECTED);
					this.rideRepository.save(ride);
					throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active drivers at the moment!");
				}
				ride.setDriver((Driver) result[0]);
				ride.setEstimatedTime((Double) result[1]);
				ride.setTotalPrice(this.calculateDistance(this.locationService.getFirstLocation(ride.getLocations()), this.locationService.getLastLocation(ride.getLocations())) * ride.getVehicleType().getPricePerKm());
				this.rideRepository.save(ride);
			};
			Calendar calendar = Calendar.getInstance();
			LocalDateTime dateTime = ride.getScheduledTime().minusMinutes(15);
			calendar.set(Calendar.YEAR, dateTime.getYear());
			calendar.set(Calendar.MONTH, dateTime.getMonth().getValue());
			calendar.set(Calendar.DAY_OF_MONTH, dateTime.getDayOfMonth());
			calendar.set(Calendar.HOUR_OF_DAY, dateTime.getHour());
			calendar.set(Calendar.MINUTE, dateTime.getMinute());
			calendar.set(Calendar.SECOND, 0);
		} else {

			Object[] result = this.findSuitableDriver(ride);
			if (result[0] == null) {
				ride.setStatus(RideStatus.REJECTED);
				this.rideRepository.save(ride);
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active drivers at the moment!");
			}
			ride.setDriver((Driver) result[0]);
			ride.setEstimatedTime((Double) result[1]);
			ride.setTotalPrice(this.calculateDistance(this.locationService.getFirstLocation(ride.getLocations()), this.locationService.getLastLocation(ride.getLocations())) * ride.getVehicleType().getPricePerKm());
		}
		return new RideDTO(this.rideRepository.save(ride));
	}

	private Object[] findSuitableDriver(Ride ride) {

		Object[] result = new Object[2];
		result[0] = null;
		result[1] = null;

		Driver bestDriver = new Driver();
		int minimumMinutes = Integer.MAX_VALUE;

		List<Driver> availableDrivers = this.driverRepository.findAllByIsActive(true);
		if(availableDrivers.isEmpty())  return result;

		List<Driver> suitableDrivers = new ArrayList<>();
		for (Driver driver: availableDrivers) {
			if(this.workingHoursService.getTotalHoursWorkedInLastDay(driver.getId()).toHours() >= 8) continue;
			if(this.getRejectedRidesForDriver(driver.getId(), ride.getPassengers().iterator().next().getId())) continue;
			if(this.findDriverScheduledRide(driver.getId()).isPresent()) continue;
			Vehicle vehicle = driverRepository.getVehicle(driver.getId());
			if(ride.getVehicleType() != vehicle.getType()) continue;
			if(ride.isBabyAccessible())
				if(!vehicle.isBabyAccessible()) continue;
			if(ride.isPetAccessible())
				if(!vehicle.isPetAccessible()) continue;
			if(ride.getPassengers().size() > vehicle.getPassengerSeats()) continue;
			suitableDrivers.add(driver);
		}

		if(suitableDrivers.isEmpty()) return result;

		for (Driver driver: suitableDrivers) {
			int minutes = 0;
			Ride currentRide = this.findDriverActiveRide(driver.getId());
			if(currentRide != null) {
				minutes += (this.calculateDistance(driver.getVehicle().getCurrentLocation(), locationService.getLastLocation(currentRide.getLocations())) / 80) * 60 * 2;
				minutes += (this.calculateDistance(locationService.getLastLocation(currentRide.getLocations()), locationService.getFirstLocation(ride.getLocations())) / 80) * 60 * 2;
			} else {
				minutes += (this.calculateDistance(driver.getVehicle().getCurrentLocation(), locationService.getFirstLocation(ride.getLocations())) / 80) * 60 * 2;
			}
			if((int)minutes < minimumMinutes){
				bestDriver = driver;
				minimumMinutes = (int) minutes;
			}
		}

		result[0] = bestDriver;
		result[1] = (int) (minimumMinutes + (this.calculateDistance(locationService.getFirstLocation(ride.getLocations()), locationService.getLastLocation(ride.getLocations())) / 80) * 60 * 2);

		return result;
	}

	private boolean getRejectedRidesForDriver(Long driverId, Long passengerId) {
		Set<Ride> rejectedRides = this.rideRepository.findAllRidesByDriverIdAndPassengerIdAndScheduledTimeBeforeAndStatus(driverId, passengerId, LocalDateTime.now().minusMinutes(15), RideStatus.REJECTED);
		if(rejectedRides.isEmpty()) return true;
		return false;
	}

	private Optional<Ride> findDriverScheduledRide(Long driverId) {
		return this.rideRepository.findRideByDriverIdAndStatus(driverId, RideStatus.ACCEPTED);
	}

	@Override
	public Ride findDriverActiveRide(Long driverId) {
		Ride ride = this.rideRepository.findRideByDriverIdAndStatus(driverId, RideStatus.ACTIVE).get();
		if(ride != null)
		{
			ride.setDriver(this.driverRepository.findDriverByRidesContaining(ride).get());
			ride.setPassengers(this.passengerService.findPassengersByRidesContaining(ride));
			ride.setLocations(this.rideRepository.getLocationsByRide(ride.getId()));
		}
		return ride;
	}

	@Override
	public Ride withdrawRide(Ride ride) {
		ride.setStatus(RideStatus.WITHDRAWN);
		return ride;
	}

	@Override
	public Ride panicRide(Ride ride, String reason) {
		this.panicRepository.save(new Panic(new Date(), reason, ride, ride.getDriver()));
		ride.setStatus(RideStatus.FINISHED);
		return ride;
	}

	@Override
	public Ride cancelRide(Ride ride, String reason) {
		ride.setStatus(RideStatus.REJECTED);
		Deduction deduction = new Deduction(ride, ride.getDriver(), reason, LocalDateTime.now());
		ride.setDeduction(deduction);
		return ride;
	}

	@Override
	public Ride endRide(Ride ride) {
		ride.setStatus(RideStatus.FINISHED);
		return ride;
	}

	@Override
	public Ride acceptRide(Ride ride) {
		ride.setStatus(RideStatus.ACCEPTED);
		return ride;
	}

	public Page<Ride> findAll(Long driverId, Pageable page, Date from, Date to){
		if(from == null && to == null)
			return this.rideRepository.findAllByDriverId(driverId, page);
		if(to != null && from == null)
			return this.rideRepository.findAllByDriverIdAndTimeOfEndBefore(driverId, to, page);
		if(to == null)
			return this.rideRepository.findAllByDriverIdAndTimeOfStartAfter(driverId, from, page);

		return this.rideRepository.findAllByDriverIdAndTimeOfStartAfterAndTimeOfEndBefore(driverId,
				from,
				to,
				page);

	}

	@Override
	public Page<Ride> findAllRidesForPassenger(Long passengerId, Pageable page, Date from, Date to) {
		Optional<Passenger> passenger = this.passengerRepository.findById(passengerId);
		if(passenger.isEmpty()) return null;

		if(from == null && to == null)
			return this.rideRepository.findAllRidesByPassengerId(passengerId, page);
		if(to != null && from == null)
			return this.rideRepository.findAllRidesByPassengerIdAndTimeOfEndBefore(passengerId, to, page);
		if(to == null)
			return this.rideRepository.findAllRidesByPassengerIdAndTimeOfStartAfter(passengerId, from, page);

		return this.rideRepository.findAllRidesByPassengerIdAndTimeOfStartAfterAndTimeOfEndBefore(passengerId,
				from,
				to,
				page);
	}

	@Override
	public Page<Ride> findAllForUserWithRole(Long userId, Pageable page, Date from, Date to, Role role) {
		if(role == Role.DRIVER) return this.findAll(userId, page, from, to);

		return this.findAllRidesForPassenger(userId, page, from ,to);
	}

	@Override
	public Ride findPassengerActiveRide(Long passengerId) {
		Ride ride = this.rideRepository.findPassengerActiveRide(passengerId, RideStatus.ACTIVE);
		if(ride != null)
		{
			ride.setDriver(this.driverRepository.findDriverByRidesContaining(ride).get());
			ride.setPassengers(this.passengerService.findPassengersByRidesContaining(ride));
			ride.setLocations(this.rideRepository.getLocationsByRide(ride.getId()));
		}
		return ride;
	}

	@Override
	public LinkedHashSet<Route> getLocationsByRide(Long rideId) {
		return this.rideRepository.getLocationsByRide(rideId);
	}

	@Override
	public UserRidesDTO getFilteredRide(Ride ride, Long driverId){

		ride.setPassengers(passengerRepository.findPassengersByRidesContaining(ride));

		Set<Review> reviews = this.reviewRepository.findAllByRideId(ride.getId());
		for(Review review:reviews){
			review.setPassenger(this.passengerRepository.findbyReviewId(review.getId()));
		}

		ride.setReview(reviews);
		ride.setDeduction(deductionRepository.findDeductionByRide(ride).orElse(new Deduction()));
		LinkedHashSet<Route> locations;
		locations = this.getLocationsByRide(ride.getId());
		for (Route location : locations) {
			location.setDestination(this.routeRepository.getDestinationByRoute(location).get());
			location.setDeparture(this.routeRepository.getDepartureByRoute(location).get());
		}

		ride.setLocations(locations);
		UserRidesDTO rideDTO = new UserRidesDTO(ride);
		if(driverId != 0L)
			rideDTO.setDriver(new UserDTO(
					this.userRepository.findById(driverId).get()

			));

		else
			rideDTO.setDriver(new UserDTO(
					this.driverRepository.findDriverByRidesContaining(ride).get()
			));

		return rideDTO;
	}

	@Override
	public ReportSumAverageDTO getReport(ReportRequestDTO reportRequestDTO) {
		long diffInMillies = Math.abs(reportRequestDTO.getTo().getTime() - reportRequestDTO.getFrom().getTime());
		long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
		if(reportRequestDTO.getTypeOfReport() == TypeOfReport.RIDES_PER_DAY){
			List<ReportDTO<Long>> reportDTOS = this.rideRepository.getRidesPerDayReport(reportRequestDTO.getFrom(), reportRequestDTO.getTo());

			return this.filterTotalRidesReports(reportDTOS, diff);
		}else if(reportRequestDTO.getTypeOfReport() == TypeOfReport.KILOMETERS_PER_DAY){
			List<RideLocationWithTimeDTO> rideLocationWithTimeDTO =
					this.rideRepository.getAllRidesWithStartTimeBetween(reportRequestDTO.getFrom(),
							reportRequestDTO.getTo());
			List<ReportDTO<Double>> reportDTOS = new ArrayList<>();
			FilterRideLocations(rideLocationWithTimeDTO, reportDTOS);
			return this.filterReports(reportDTOS, diff);

		}else if(reportRequestDTO.getTypeOfReport() == TypeOfReport.MONEY_SPENT_PER_DAY){
			List<ReportDTO<Double>> reportDTOS = this.rideRepository.getTotalCostPerDay(reportRequestDTO.getFrom(), reportRequestDTO.getTo());
			return this.filterReports(reportDTOS, diff);

		}else if(reportRequestDTO.getTypeOfReport() == TypeOfReport.MONEY_EARNED_PER_DAY){
			List<ReportDTO<Double>> reportDTOS = this.rideRepository.getTotalCostPerDay(reportRequestDTO.getFrom(), reportRequestDTO.getTo());
			return this.filterReports(reportDTOS, diff);

		}

		return null;

	}

	@Override
	public ReportSumAverageDTO getReportForDriver(ReportRequestDTO reportRequestDTO) {
		long diffInMillies = Math.abs(reportRequestDTO.getTo().getTime() - reportRequestDTO.getFrom().getTime());
		long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
		long driverId = reportRequestDTO.getDriverId();

		if(reportRequestDTO.getTypeOfReport() == TypeOfReport.RIDES_PER_DAY){
			List<ReportDTO<Long>> reportDTOS = this.rideRepository.getRidesPerDayForSpecificDriver(reportRequestDTO.getFrom(),
					reportRequestDTO.getTo(), driverId);

			return this.filterTotalRidesReports(reportDTOS, diff);
		}else if(reportRequestDTO.getTypeOfReport() == TypeOfReport.KILOMETERS_PER_DAY){

			List<RideLocationWithTimeDTO> rideLocationWithTimeDTO =
					this.rideRepository.getRidesWithStartTimeBetweenForSpecificDriver(reportRequestDTO.getFrom(),
							reportRequestDTO.getTo(), driverId);

			List<ReportDTO<Double>> reportDTOS = new ArrayList<>();
			FilterRideLocations(rideLocationWithTimeDTO, reportDTOS);
			return this.filterReports(reportDTOS, diff);

		}else if(reportRequestDTO.getTypeOfReport() == TypeOfReport.MONEY_SPENT_PER_DAY){

			List<ReportDTO<Double>> reportDTOS = this.rideRepository.getTotalCostPerDayForSpecificDriver(
					reportRequestDTO.getFrom(), reportRequestDTO.getTo(), driverId);
			return this.filterReports(reportDTOS, diff);

		}else if(reportRequestDTO.getTypeOfReport() == TypeOfReport.MONEY_EARNED_PER_DAY){
			List<ReportDTO<Double>> reportDTOS = this.rideRepository.getTotalCostPerDayForDriver(
					reportRequestDTO.getFrom(), reportRequestDTO.getTo(), driverId);
			return this.filterReports(reportDTOS, diff);

		}

		return null;
	}

	private void FilterRideLocations(List<RideLocationWithTimeDTO> rideLocationWithTimeDTO, List<ReportDTO<Double>> reportDTOS) {
		for(RideLocationWithTimeDTO r: rideLocationWithTimeDTO){
			Set<Route> routes = this.rideRepository.getLocationsByRide(r.getRideId());
			for(Route route: routes){
				route.setDeparture(this.routeRepository.getDepartureByRoute(route).get());
				route.setDestination(this.routeRepository.getDestinationByRoute(route).get());
			}
			r.setLocations(routes);
			List<Route> routesListed = r.getLocations().stream().toList();
			double distance = this.calculateDistance(routesListed.get(0).getDeparture(),
													routesListed.get(routesListed.size() - 1).getDestination());
			reportDTOS.add(new ReportDTO<>(r.getStartTime(), distance));
		}
	}

	@Override
	public ReportSumAverageDTO filterTotalRidesReports(List<ReportDTO<Long>> reportDTOS, long totalDays){
		ReportSumAverageDTO reportSumAverageDTO = new ReportSumAverageDTO();

		Map<Date, Double> reports = new LinkedHashMap<>();
		double sum = 0;
		for(ReportDTO<Long> report: reportDTOS){
			Date date = getFormattedDate(report);
			if(reports.containsKey(date)){
				reports.computeIfPresent(date, (k, v) -> v + (double)(report.getTotal()));
			}else{
				reports.put(date, (double)(report.getTotal()));
			}
			sum += report.getTotal();

		}
		reportSumAverageDTO.setResult(reports);
		reportSumAverageDTO.setSum(sum);
		reportSumAverageDTO.setAverage(sum/ totalDays);
		return reportSumAverageDTO;
	}

	@Override
	public double calculateDistance(Location departure, Location destination) {
		double theta = departure.getLongitude() - destination.getLongitude();
		double dist = Math.sin(Math.toRadians(departure.getLatitude())) * Math.sin(Math.toRadians(destination.getLatitude()))
				+ Math.cos(Math.toRadians(departure.getLatitude())) * Math.cos(Math.toRadians(destination.getLatitude())) * Math.cos(Math.toRadians(theta));
		dist = Math.acos(dist);
		dist = Math.toDegrees(dist);
		dist = dist * 60 * 1.1515;
		dist = dist * 1.609344;
		return dist;
	}

	@Override
	public ReportSumAverageDTO filterReports(List<ReportDTO<Double>> reportDTOS, long totalDays) {

		ReportSumAverageDTO reportSumAverageDTO = new ReportSumAverageDTO();
		Map<Date, Double> reports = new LinkedHashMap<>();
		double sum = 0;
		for(ReportDTO<Double> report: reportDTOS){
			Date date = getFormattedDate(report);
			if(reports.containsKey(date)){
				reports.computeIfPresent(date, (k, v) -> v + report.getTotal());
			}else{
				reports.put(date, report.getTotal());
			}
			sum += report.getTotal();
		}
		reportSumAverageDTO.setResult(reports);
		reportSumAverageDTO.setSum(sum);
		reportSumAverageDTO.setAverage(sum/ totalDays);

		return reportSumAverageDTO;
	}

	private Date getFormattedDate(ReportDTO report) {
		Date date = report.getDate();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
		String dateStr = sdf.format(date);
		try {
			return sdf.parse(dateStr);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public Set<UserRidesDTO> getFilteredRides(Page<Ride> userRides, Long driverId) {
		Set<UserRidesDTO> rides = new LinkedHashSet<>();
		for (Ride ride : userRides) {

			rides.add(this.getFilteredRide(ride, driverId));
		}

		return rides;
	}

}
