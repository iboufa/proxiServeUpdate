package tn.fst.proxiserve.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import tn.fst.proxiserve.model.Booking;

public interface BookingRepository extends MongoRepository<Booking, String> {
    List<Booking> findByClientId(String clientId);
    List<Booking> findByArtisanId(String artisanId);
    List<Booking> findByServiceIdIn(List<String> serviceIds);
    List<Booking> findByClientIdAndStatus(String clientId, String status);
    


}