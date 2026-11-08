package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.ActivityWorkGroupRestriction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharingShopConfiguration {
    @Bean
    AllocationPolicy createSharingShopAllocationPolicy() {
        return new PreferenceAllocationPolicy();
    }

    @Bean
    ActivityWorkGroupRestriction createRestaurantSharingRestriction() {
        return new RestaurantSharingRestriction();
    }
}
