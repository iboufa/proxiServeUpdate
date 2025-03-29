package tn.fst.proxiserve.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tn.fst.proxiserve.model.Artisan;
import tn.fst.proxiserve.model.ServiceEntity;
import tn.fst.proxiserve.repository.ArtisanRepository;
import tn.fst.proxiserve.repository.ServiceRepository;
import tn.fst.proxiserve.service.ArtisanService;

@RestController
@RequestMapping("/api/services/search")
@RequiredArgsConstructor
public class ServiceSearchController {

    private final ServiceRepository serviceRepository;
    private final ArtisanRepository artisanRepository;
    

    private final ArtisanService artisanService;


    

    @GetMapping("/advanced")
    public ResponseEntity<List<Map<String, Object>>> advancedSearch(
            @RequestParam(required = false) String query,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "distance") String sortBy) {

        List<ServiceEntity> services = serviceRepository.findAll();
        List<Map<String, Object>> results = new ArrayList<>();

        for (ServiceEntity service : services) {
            Optional<Artisan> artisanOpt = artisanRepository.findById(service.getArtisanId());
            if (artisanOpt.isPresent()) {
                Artisan artisan = artisanOpt.get();
                GeoJsonPoint loc = artisan.getLocation();
                double distance = haversineDistance(latitude, longitude, loc.getCoordinates().get(1), loc.getCoordinates().get(0));

                boolean matchesQuery = (query == null ||
                        service.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                        (service.getDescription() != null && service.getDescription().toLowerCase().contains(query.toLowerCase())));

                if (distance <= radiusKm && matchesQuery) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", service.getId());
                    item.put("title", service.getTitle());
                    item.put("description", service.getDescription());
                    item.put("price", service.getPrice());
                    item.put("artisanId", service.getArtisanId());
                    item.put("distanceKm", Math.round(distance * 10.0) / 10.0);
                    double rating = artisanService.calculateAverageRating(artisan.getId());
                    item.put("rating", rating);
                
                    results.add(item);
                }
            }
        }

        Comparator<Map<String, Object>> comparator;

        switch (sortBy) {
            case "price":
                comparator = Comparator.comparing(s -> (Double) s.get("price"));
                break;
            case "rating":
                comparator = Comparator.comparing(
                    s -> (Double) s.getOrDefault("rating", 0.0),
                    Comparator.reverseOrder()
                );
                break;
            default:
                comparator = Comparator.comparing(s -> (Double) s.get("distanceKm"));
                break;
        }
        

        List<Map<String, Object>> sorted = results.stream()
                .sorted(comparator)
                .collect(Collectors.toList());

        List<Map<String, Object>> paginated = sorted.stream()
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());

        return ResponseEntity.ok(paginated);
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
