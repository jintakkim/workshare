package com.jangbokhuman.workshare.activity.sharingshop;

import com.jangbokhuman.workshare.activity.ActivityWorkGroupRestriction;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SharingShopSettings.class)
public class SharingShopConfiguration {
    @Bean
    TaskAllocationPolicy createTaskAllocationPolicy() {
        return new RandomTaskAllocationPolicy();
    }

    @Bean
    AllocationPolicy createSharingShopAllocationPolicy() {
        return new PreferenceAllocationPolicy();
    }

    @Bean
    ActivityWorkGroupRestriction createRestaurantSharingRestriction() {
        return new RestaurantSharingRestriction();
    }
}
