package com.product.controller;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.product.Exception.ProductNotFoundException;
import com.product.domain.Product;
import com.product.domain.ProductDto;
import com.product.domain.ReviewDto;
import com.product.service.ProductReviewService;
import com.product.service.ProductService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.*;

@RestController
public class ProductServiceController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductReviewService productReviewService;

    @Autowired
    EurekaClient eurekaClient;

    @Autowired
    RestTemplate restTemplate;

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    Logger log = LoggerFactory.getLogger(ProductServiceController.class);

    private String SERVICE_NAME="REVIEW-SERVICE";


    private String getServiceUrl() {
        InstanceInfo instance = eurekaClient.getNextServerFromEureka(SERVICE_NAME, false);
        return instance.getHomePageUrl();
    }
    @PostMapping("/products")
    public ResponseEntity<Object> createProduct(@RequestBody ProductDto productDto) {

        Product savedProduct = productService.saveProduct(convertToEntity(productDto));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(savedProduct.getId()).toUri();
        return ResponseEntity.created(location).build();
    }


    @GetMapping(value = "/product/{id}")
    public Map<String, Object> getProductDetails(@PathVariable long id) {

        log.info("getting product details");
        Optional<Product> productOptional = productService.findProductById(id);
        Map<String, Object> result = new HashMap<>();
        if (!productOptional.isPresent()) {
            log.info("no data present");
            throw new ProductNotFoundException("id-" + id + "is not available");
        }
        result.put("productDetails", convertToDto(productOptional.get()));
        List<ReviewDto> reviewDtoList = getReviewDetails(id);
        result.put("reviews", productReviewService.getProductReviews(id));
        return result;
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<Object> updateProduct(@RequestBody ProductDto productDto, @PathVariable long id) {

        Optional<Product> productOptional = productService.findProductById(id);
        if (!productOptional.isPresent())
            return ResponseEntity.notFound().build();

        Product product = updateProductEntity(productDto, productOptional.get());

        productService.addProduct(product);
        return ResponseEntity.noContent().build();
    }


    @DeleteMapping(value = "/product/{id}")
    public void deleteProduct(@PathVariable long id) {
        log.info("deleting product :{}", id);
        productService.deleteProduct(id);
    }

    @PostMapping(value = "/product/reviews/{id}")
    public ResponseEntity<Object> addProductReview(@RequestBody ReviewDto reviewDto, @PathVariable int id) {

        ResponseEntity<Object> responseEntity = productReviewService.addReview(reviewDto);
        if (responseEntity.getStatusCode() != HttpStatus.CREATED) {
            log.info("unable to add review");
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id).toUri();
        return ResponseEntity.created(location).build();
    }


    private Product updateProductEntity(ProductDto productDto, Product product) {

        Product productEntity = convertToEntity(productDto);
        productEntity.setId(product.getId());
        if (StringUtils.isEmpty(productEntity.getName())) {
            productEntity.setName(product.getName());
        }
        if (StringUtils.isEmpty(product.getDescription())) {
            productEntity.setName(product.getDescription());
        }
        return productEntity;
    }

    private List<ReviewDto> getReviewDetails(long productId) {
        List<ReviewDto> reviewDtoList = new ArrayList<>();
        String uri = getServiceUrl()+"/{productId}/reviews";
        Map<String, String> params = new HashMap<>();
        params.put("productId", "1");
        ResponseEntity<Object> responseEntity = restTemplate.exchange(uri, HttpMethod.GET, null, Object.class, params);
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            log.info("unable to add review");
        } else {
            reviewDtoList = (List<ReviewDto>) responseEntity.getBody();
        }

        return reviewDtoList;
    }

    private ProductDto convertToDto(Product product) {
        return modelMapper().map(product, ProductDto.class);
    }

    private Product convertToEntity(ProductDto productDto) {
        return modelMapper().map(productDto, Product.class);
    }
}


