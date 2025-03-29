package tn.fst.proxiserve.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import tn.fst.proxiserve.model.ServiceEntity;

public interface ServiceRepository extends MongoRepository<ServiceEntity, String> {

    List<ServiceEntity> findByArtisanId(String artisanId);

    
}
