package tn.fst.proxiserve.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tn.fst.proxiserve.dto.RatingStatsView;
import tn.fst.proxiserve.model.Review;
import tn.fst.proxiserve.repository.ReviewRepository;


@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public RatingStatsView getRatingStatsForArtisan(String artisanId) {
        List<Review> reviews = reviewRepository.findByArtisanId(artisanId);

        long total = reviews.size();

        double average = total == 0 ? 0.0 :
                reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);

        Map<Integer, Long> distribution = reviews.stream()
                .collect(Collectors.groupingBy(Review::getRating, Collectors.counting()));

        return new RatingStatsView(artisanId, average, total, distribution);
    }
}
