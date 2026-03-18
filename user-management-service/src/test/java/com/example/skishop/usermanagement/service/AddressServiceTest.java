package com.example.skishop.usermanagement.service;

import com.example.skishop.usermanagement.dto.request.AddressRequest;
import com.example.skishop.usermanagement.dto.response.AddressResponse;
import com.example.skishop.usermanagement.exception.ResourceNotFoundException;
import com.example.skishop.usermanagement.model.Address;
import com.example.skishop.usermanagement.model.AddressType;
import com.example.skishop.usermanagement.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @InjectMocks
    private AddressService addressService;

    @Mock
    private AddressRepository addressRepository;

    private UUID userId;
    private Address testAddress;
    private AddressRequest addressRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        testAddress = new Address();
        testAddress.setId(1L);
        testAddress.setUserId(userId);
        testAddress.setAddressType(AddressType.SHIPPING);
        testAddress.setRecipient("山田太郎");
        testAddress.setZipCode("123-4567");
        testAddress.setPrefecture("東京都");
        testAddress.setCity("新宿区");
        testAddress.setStreetAddress("1-1-1");
        testAddress.setDefault(true);

        addressRequest = new AddressRequest(
            AddressType.SHIPPING, "山田太郎", "123-4567",
            "東京都", "新宿区", "1-1-1", null, null, true
        );
    }

    @Test
    @DisplayName("ユーザーIDで住所を取得した場合、住所リストを返す")
    void should_returnAddressList_when_getAddressesByUserId() {
        // Arrange
        when(addressRepository.findByUserId(userId)).thenReturn(List.of(testAddress));

        // Act
        List<AddressResponse> result = addressService.getAddresses(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(userId);
        assertThat(result.get(0).recipient()).isEqualTo("山田太郎");
        verify(addressRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("住所を追加した場合、保存された住所を返す")
    void should_returnSavedAddress_when_addAddress() {
        // Arrange
        when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

        // Act
        AddressResponse result = addressService.addAddress(userId, addressRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.recipient()).isEqualTo("山田太郎");
        assertThat(result.zipCode()).isEqualTo("123-4567");
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    @DisplayName("存在する住所を更新した場合、更新された住所を返す")
    void should_returnUpdatedAddress_when_addressExists() {
        // Arrange
        when(addressRepository.findByIdAndUserId(1L, userId)).thenReturn(Optional.of(testAddress));
        when(addressRepository.save(any(Address.class))).thenReturn(testAddress);

        // Act
        AddressResponse result = addressService.updateAddress(userId, 1L, addressRequest);

        // Assert
        assertThat(result).isNotNull();
        verify(addressRepository).findByIdAndUserId(1L, userId);
        verify(addressRepository).save(testAddress);
    }

    @Test
    @DisplayName("存在しない住所を更新しようとした場合、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_addressNotFoundForUpdate() {
        // Arrange
        when(addressRepository.findByIdAndUserId(999L, userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> addressService.updateAddress(userId, 999L, addressRequest))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Address が見つかりません: 999");
        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("存在する住所を削除した場合、削除が実行される")
    void should_deleteAddress_when_addressExists() {
        // Arrange
        when(addressRepository.findByIdAndUserId(1L, userId)).thenReturn(Optional.of(testAddress));

        // Act
        addressService.deleteAddress(userId, 1L);

        // Assert
        verify(addressRepository).delete(testAddress);
    }

    @Test
    @DisplayName("存在しない住所を削除しようとした場合、ResourceNotFoundExceptionをスローする")
    void should_throwResourceNotFoundException_when_addressNotFoundForDelete() {
        // Arrange
        when(addressRepository.findByIdAndUserId(999L, userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> addressService.deleteAddress(userId, 999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Address が見つかりません: 999");
        verify(addressRepository, never()).delete(any());
    }
}
