package com.afrochow.image;

import com.afrochow.common.enums.Role;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.vendor.repository.VendorProfileRepository;
import com.afrochow.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Authorization check backing {@code DELETE /images} (see ImageController).
 *
 * <p>There's no table that records "who uploaded this image" — uploads go
 * straight to Cloudinary from the frontend with an unsigned preset, and the
 * URL only becomes associated with an owner once it's saved onto a
 * VendorProfile (logo/banner/business license) or Product row. Before that
 * point — e.g. mid-registration-wizard, where step-2/step-3 upload a file,
 * let the user preview it, and call deleteImage() to discard it if they
 * re-upload or go back — the URL isn't attached to anything yet.
 *
 * <p>So ownership is decided in two steps:
 * <ol>
 *   <li>If the URL is currently attached to the caller's own VendorProfile or
 *       one of their own products, allow it — they're editing their own data.</li>
 *   <li>If the URL is NOT attached to anyone's saved record at all, allow it —
 *       it's an orphaned/in-progress upload, and since nothing has claimed it
 *       yet the uploader is the only plausible owner.</li>
 * </ol>
 * Anything else — a URL currently saved on a DIFFERENT user's profile or
 * product — is denied. That's the actual gap this closes: previously any
 * authenticated user could delete any other user's live logo, banner,
 * business license, or product photo just by knowing/guessing its URL.
 */
@Service
@RequiredArgsConstructor
public class ImageOwnershipService {

    private final VendorProfileRepository vendorProfileRepository;
    private final ProductRepository productRepository;

    public boolean canDelete(String imageUrl, Long currentUserId, Role currentUserRole) {
        if (currentUserRole == Role.ADMIN || currentUserRole == Role.SUPERADMIN) {
            return true;
        }

        Optional<VendorProfile> ownVendorProfile = currentUserId != null
                ? vendorProfileRepository.findByUser_UserId(currentUserId)
                : Optional.empty();

        if (ownVendorProfile.isPresent()) {
            VendorProfile vp = ownVendorProfile.get();
            boolean isOwnProfileImage = imageUrl.equals(vp.getLogoUrl())
                    || imageUrl.equals(vp.getBannerUrl())
                    || imageUrl.equals(vp.getBusinessLicenseUrl());
            if (isOwnProfileImage) {
                return true;
            }
            if (productRepository.existsByVendorAndImageUrl(vp, imageUrl)) {
                return true;
            }
        }

        boolean claimedByAnyVendorProfile = vendorProfileRepository
                .existsByLogoUrlOrBannerUrlOrBusinessLicenseUrl(imageUrl, imageUrl, imageUrl);
        boolean claimedByAnyProduct = productRepository.existsByImageUrl(imageUrl);

        // Not saved anywhere — orphaned/in-progress upload, safe to let the
        // caller clean it up themselves.
        return !claimedByAnyVendorProfile && !claimedByAnyProduct;
    }
}
