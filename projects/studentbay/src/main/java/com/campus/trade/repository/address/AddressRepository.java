package com.campus.trade.repository.address;

import com.campus.trade.entity.address.DeliveryAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<DeliveryAddress, Long> {
    List<DeliveryAddress> findByUserIdOrderByDefaultAddressDescIdDesc(Long userId);
}
