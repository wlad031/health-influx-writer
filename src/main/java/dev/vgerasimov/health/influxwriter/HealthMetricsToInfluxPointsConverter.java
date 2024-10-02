package dev.vgerasimov.health.influxwriter;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.vgerasimov.health.influxwriter.HealthMetricsToInfluxPointsConverter.Measurement.Field;
import dev.vgerasimov.health.influxwriter.HealthMetricsToInfluxPointsConverter.Payload.Data.Metric;
import dev.vgerasimov.health.influxwriter.HealthMetricsToInfluxPointsConverter.Payload.Data.Metric.DataPoint;
import dev.vgerasimov.pipes.CustomStep;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.Builder;
import reactor.core.publisher.Flux;

public class HealthMetricsToInfluxPointsConverter extends CustomStep {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private final Map<String, String> customTags;

  HealthMetricsToInfluxPointsConverter(String name, @Nullable String customPropertiesString) {
    super(name, customPropertiesString);
    log.info("Custom properties: {}", customPropertiesString);

    Map<String, String> customTags = new HashMap<>();
    if (isNotBlank(customPropertiesString)) {
      try {
        var mapProperties = objectMapper.readValue(customPropertiesString, CustomProperties.class);
        customTags = mapProperties.tags();
      } catch (Exception e) {
        log.error("Error parsing custom properties string: '{}'", customPropertiesString, e);
      }
    }
    this.customTags = customTags;

    log.info("Custom tags: {}", this.customTags);
  }

  @Override
  public Flux<Map<String, Object>> apply(Map<String, Object> payload) {
    if (payload == null) {
      log.error("Payload is null");
      return Flux.empty();
    }
    if (payload.isEmpty()) {
      log.warn("Payload is empty");
      return Flux.just(payload);
    }

    Payload payloadObject;
    try {
      payloadObject = objectMapper.convertValue(payload, Payload.class);
      log.debug("Payload parsed successfully");
    } catch (Exception e) {
      log.error("Error parsing payload", e);
      return Flux.empty();
    }

    Measurements measurements;
    try {
      measurements = createMeasurements(payloadObject.data().metrics());
      log.debug("Measurements created successfully");
    } catch (Exception e) {
      return Flux.error(e);
    }

    Map<String, Object> result;
    try {
      result = objectMapper.convertValue(measurements, new TypeReference<>() {});
      log.debug("Result converted to map successfully");
    } catch (Exception e) {
      return Flux.error(e);
    }

    return Flux.just(result);
  }

  private Measurements createMeasurements(List<Payload.Data.Metric> metrics) {
    var result = new ArrayList<Measurement>();
    for (var metric : metrics) {
      var tags = createTags(metric);
      for (var data : metric.data()) {
        result.add(
            Measurement.builder()
                .name(metric.name())
                .tags(tags)
                .timestamp(data.date().toEpochSecond())
                .fields(createFields(data))
                .build());
      }
    }
    return new Measurements(result);
  }

  private Map<String, String> createTags(Metric metric) {
    HashMap<String, String> result = new HashMap<>(customTags);
    result.put("units", metric.units());
    return result;
  }

  // TODO: Move to common package
  private static BigDecimal toBigDecimal(OffsetDateTime odt) {
    return BigDecimal.valueOf(odt.toEpochSecond());
  }

  private static List<Field> createFields(DataPoint dp) {
    return new CollectionBuilder<Field, List<Field>>(ArrayList::new)
        .addIfNotNull(dp.qty(), v -> new Field("qty", v))
        .addIfNotNull(dp.max(), v -> new Field("max", v))
        .addIfNotNull(dp.min(), v -> new Field("min", v))
        .addIfNotNull(dp.avg(), v -> new Field("avg", v))
        .addIfNotNull(dp.deep(), v -> new Field("deep", v))
        .addIfNotNull(dp.inBedEnd(), v -> new Field("inBedEnd", toBigDecimal(v)))
        .addIfNotNull(dp.asleep(), v -> new Field("asleep", v))
        .addIfNotNull(dp.core(), v -> new Field("core", v))
        .addIfNotNull(dp.rem(), v -> new Field("rem", v))
        .addIfNotNull(dp.inBedStart(), v -> new Field("inBedStart", toBigDecimal(v)))
        .addIfNotNull(dp.sleepStart(), v -> new Field("sleepStart", toBigDecimal(v)))
        .addIfNotNull(dp.sleepEnd(), v -> new Field("sleepEnd", toBigDecimal(v)))
        .addIfNotNull(dp.inBed(), v -> new Field("inBed", toBigDecimal(v)))
        .addIfNotNull(dp.awake(), v -> new Field("awake", v))
        .addIfNotNull(dp.diastolic(), v -> new Field("diastolic", v))
        .addIfNotNull(dp.systolic(), v -> new Field("systolic", v))
        .build();
  }

  // TODO: Move to common package
  public static class CollectionBuilder<T, CC extends Collection<T>> {
    private final CC collection;

    public CollectionBuilder(Supplier<? extends CC> collectionCreator) {
      this.collection = collectionCreator.get();
    }

    /**
     * If given value satisfies given predicate, maps it using given function and adds result to the
     * result.
     */
    public <S> CollectionBuilder<T, CC> addIf(@Nullable S v, Predicate<S> p, Function<S, T> f) {
      if (p.test(v)) collection.add(f.apply(v));
      return this;
    }

    /** If given value is not null, maps it using given function and adds result to the result. */
    public <S> CollectionBuilder<T, CC> addIfNotNull(@Nullable S v, Function<S, T> f) {
      return addIf(v, Objects::nonNull, f);
    }

    public CC build() {
      return build(Function.identity());
    }

    public <R extends Collection<T>> R build(Function<CC, R> finalizer) {
      return finalizer.apply(collection);
    }
  }

  record CustomProperties(Map<String, String> tags) {}

  record Payload(Data data) {
    record Data(
        List<Metric> metrics,
        List<Map<String, Object>> workouts,
        List<Map<String, Object>> ecg,
        List<Map<String, Object>> heartRateNotifications,
        List<Map<String, Object>> symptoms) {
      record Metric(String name, String units, List<DataPoint> data) {
        record DataPoint(
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss Z") @JsonProperty("date")
                OffsetDateTime date,
            @JsonProperty("source") @Nullable String source,
            @JsonProperty("qty") @Nullable BigDecimal qty,
            @JsonProperty("Max") @Nullable BigDecimal max,
            @JsonProperty("Min") @Nullable BigDecimal min,
            @JsonProperty("Avg") @Nullable BigDecimal avg,
            @JsonProperty("deep") @Nullable BigDecimal deep,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss Z") @JsonProperty("inBedEnd")
                OffsetDateTime inBedEnd,
            @JsonProperty("asleep") @Nullable BigDecimal asleep,
            @JsonProperty("core") @Nullable BigDecimal core,
            @JsonProperty("rem") @Nullable BigDecimal rem,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss Z") @JsonProperty("inBedStart")
                OffsetDateTime inBedStart,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss Z") @JsonProperty("sleepStart")
                OffsetDateTime sleepStart,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss Z") @JsonProperty("sleepEnd")
                OffsetDateTime sleepEnd,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss Z") @JsonProperty("inBed")
                OffsetDateTime inBed,
            @JsonProperty("awake") @Nullable BigDecimal awake,
            @JsonProperty("diastolic") @Nullable BigDecimal diastolic,
            @JsonProperty("systolic") @Nullable BigDecimal systolic,
            @JsonProperty("Unspecified") @Nullable String unspecified,
            @JsonProperty("Protection Used") @Nullable String protectionUsed,
            @JsonProperty("Protection Not Used") @Nullable String protectionNotUsed) {}
      }
    }
  }

  record Measurements(List<Measurement> measurements) {}

  @Builder
  record Measurement(String name, long timestamp, List<Field> fields, Map<String, String> tags) {
    record Field(String name, BigDecimal value) {}
  }
}
