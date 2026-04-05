package com.afrochow.review.event;

public record VendorReviewedEvent(String vendorPublicId, String reviewerName, Integer rating, String reviewType) {}
