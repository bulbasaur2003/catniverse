package com.catniverse.backend.service.order;

import com.catniverse.backend.dto.OrderDto;
import com.catniverse.backend.dto.OrderItemDto;
import com.catniverse.backend.enums.OrderStatus;
import com.catniverse.backend.exceptions.ResourceNotFoundException;
import com.catniverse.backend.model.*;
import com.catniverse.backend.repo.OrderRepo;
import com.catniverse.backend.repo.ProductRepo;
import com.catniverse.backend.service.cart.CartService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService implements ImpOrderService{
    private final OrderRepo orderRepo;
    private final ProductRepo productRepo;
    private final CartService cartService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public OrderDto placeOrder(Long userId) {
        Cart cart = cartService.getCartByUserId(userId);
        Order order = createOrder(cart);
        List<OrderItem> orderItemList = createOrderItems(order, cart);
        order.setOrderItems(new HashSet<>(orderItemList));
        order.setTotalAmount(calculateTotalAmount(orderItemList));
        Order savedOrder = orderRepo.save(order);
        cartService.clearCart(cart.getId());
        return convertToDto(savedOrder);
    }
    // ↑↑↑↑↑↑↑↑↑↑
    private Order createOrder(Cart cart) {
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDateTime.now());
        return order;
    }
    // ↑↑↑↑↑↑↑↑↑↑ keep track of the inventory
    private List<OrderItem> createOrderItems(Order order, Cart cart) {
        return cart.getItems()
                .stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();
                    product.setInventory(product.getInventory() - cartItem.getQuantity());
                    productRepo.save(product);
                    return new OrderItem(
                            order,
                            product,
                            cartItem.getQuantity(),
                            cartItem.getUnitPrice()
                    );
                }).toList();
    }
    // ↑↑↑↑↑↑↑↑↑↑
    private BigDecimal calculateTotalAmount(List<OrderItem> orderItemList){
        return orderItemList
                .stream()
                .map(item -> item.getPrice()
                        .multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }



    @Override
    public OrderDto getOrder(Long orderId) {
        return orderRepo.findById(orderId)
                .map(this::convertToDto)
                .orElseThrow(()->new ResourceNotFoundException("Order not found"));
    }

    @Override
    public List<OrderDto> getUserOrders(Long userId) {
        List<Order> orders = orderRepo.findByUserId(userId);
        return orders
                .stream()
                .map(this::convertToDto)
                .toList();
    }

    private OrderDto convertToDto(Order order) {
        OrderDto orderDto = modelMapper.map(order, OrderDto.class);
        order.getOrderItems()
                .stream()
                .map(orderItem -> {
                    Product product = productRepo.findById(orderItem.getId()).orElse(null);
                    ProductImage productImage = product.getProductImages().stream().findFirst().orElse(null);
                    productImage.getDownloadUrl();
                    return orderItem;
                });
        return orderDto;
    }


}
