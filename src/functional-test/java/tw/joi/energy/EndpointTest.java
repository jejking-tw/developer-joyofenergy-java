package tw.joi.energy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tw.joi.energy.builders.StoreReadingsRequestBuilder;
import tw.joi.energy.controller.StoreReadingsRequest;
import tw.joi.energy.domain.ElectricityReading;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class)
public class EndpointTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static HttpEntity<StoreReadingsRequest> toHttpEntity(StoreReadingsRequest meterReadings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(meterReadings, headers);
    }

    @Test
    public void shouldStoreReadings() {
        StoreReadingsRequest meterReadings =
                new StoreReadingsRequestBuilder().generateElectricityReadings().build();
        HttpEntity<StoreReadingsRequest> entity = toHttpEntity(meterReadings);

        ResponseEntity<String> response = restTemplate.postForEntity("/readings/store", entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    public void givenMeterIdShouldReturnAMeterReadingAssociatedWithMeterId() {
        String smartMeterId = "alice";
        List<ElectricityReading> data = List.of(
                new ElectricityReading(Instant.parse("2024-04-26T00:00:10.00Z"), new BigDecimal(10)),
                new ElectricityReading(Instant.parse("2024-04-26T00:00:20.00Z"), new BigDecimal(20)),
                new ElectricityReading(Instant.parse("2024-04-26T00:00:30.00Z"), new BigDecimal(30)));
        populateReadingsForMeter(smartMeterId, data);

        ResponseEntity<ElectricityReading[]> response =
                restTemplate.getForEntity("/readings/read/" + smartMeterId, ElectricityReading[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Arrays.asList(response.getBody())).isEqualTo(data);
    }

    @Test
    public void shouldCalculateAllPrices() {
        String smartMeterId = "bob";
        List<ElectricityReading> data = List.of(
                new ElectricityReading(Instant.parse("2024-04-26T00:00:10.00Z"), new BigDecimal(10)),
                new ElectricityReading(Instant.parse("2024-04-26T00:00:20.00Z"), new BigDecimal(20)),
                new ElectricityReading(Instant.parse("2024-04-26T00:00:30.00Z"), new BigDecimal(30)));
        populateReadingsForMeter(smartMeterId, data);

        ResponseEntity<CompareAllResponse> response =
                restTemplate.getForEntity("/price-plans/compare-all/" + smartMeterId, CompareAllResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(new CompareAllResponse(
                        Map.of("price-plan-0", 36000, "price-plan-1", 7200, "price-plan-2", 3600), null));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void givenMeterIdAndLimitShouldReturnRecommendedCheapestPricePlans() {
        String smartMeterId = "jane";
        List<ElectricityReading> data = List.of(
                new ElectricityReading(Instant.parse("2024-04-26T00:00:10.00Z"), new BigDecimal(10)),
                new ElectricityReading(Instant.parse("2024-04-26T00:00:20.00Z"), new BigDecimal(20)),
                new ElectricityReading(Instant.parse("2024-04-26T00:00:30.00Z"), new BigDecimal(30)));
        populateReadingsForMeter(smartMeterId, data);

        ResponseEntity<Map[]> response =
                restTemplate.getForEntity("/price-plans/recommend/" + smartMeterId + "?limit=2", Map[].class);

        assertThat(response.getBody()).containsExactly(Map.of("price-plan-2", 3600), Map.of("price-plan-1", 7200));
    }

    private void populateReadingsForMeter(String smartMeterId, List<ElectricityReading> data) {
        StoreReadingsRequest readings = new StoreReadingsRequest(smartMeterId, data);

        HttpEntity<StoreReadingsRequest> entity = toHttpEntity(readings);
        restTemplate.postForEntity("/readings/store", entity, String.class);
    }

    record CompareAllResponse(Map<String, Integer> pricePlanComparisons, String pricePlanId) {}
}
