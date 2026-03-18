package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.AddressRequest;
import com.example.skishop.usermanagement.dto.response.AddressResponse;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.Address;
import com.example.skishop.usermanagement.repository.AddressRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(UUID userId) {
        return addressRepository.findByUserId(userId).stream()
            .map(this::toAddressResponse)
            .toList();
    }

    public AddressResponse addAddress(UUID userId, AddressRequest request) {
        Address address = new Address();
        address.setUserId(userId);
        mapRequestToAddress(request, address);
        Address saved = addressRepository.save(address);
        log.info("住所を追加しました: userId={}, addressId={}", userId, saved.getId());
        return toAddressResponse(saved);
    }

    public AddressResponse updateAddress(UUID userId, Long addressId, AddressRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        mapRequestToAddress(request, address);
        Address saved = addressRepository.save(address);
        log.info("住所を更新しました: userId={}, addressId={}", userId, addressId);
        return toAddressResponse(saved);
    }

    public void deleteAddress(UUID userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        addressRepository.delete(address);
        log.info("住所を削除しました: userId={}, addressId={}", userId, addressId);
    }

    private void mapRequestToAddress(AddressRequest request, Address address) {
        address.setAddressType(request.addressType());
        address.setRecipient(request.recipient());
        address.setZipCode(request.zipCode());
        address.setPrefecture(request.prefecture());
        address.setCity(request.city());
        address.setStreetAddress(request.streetAddress());
        address.setBuilding(request.building());
        address.setPhoneNumber(request.phoneNumber());
        address.setDefault(request.isDefault());
    }

    private AddressResponse toAddressResponse(Address address) {
        return new AddressResponse(
            address.getId(),
            address.getUserId(),
            address.getAddressType(),
            address.getRecipient(),
            address.getZipCode(),
            address.getPrefecture(),
            address.getCity(),
            address.getStreetAddress(),
            address.getBuilding(),
            address.getPhoneNumber(),
            address.isDefault()
        );
    }
}
