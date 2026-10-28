package com.zhiyu.modules.growth;

import com.zhiyu.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/growth")
public class GrowthController {
    private final GrowthService service;
    private final ScaleCatalog scales;
    private final ReflectionSummaryService reflectionSummaryService;
    public record BookingReview(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="CONFIRM|REJECT") String decision) {}
    @PostMapping("/programs/{id}/bookings/{bookingId}/review") public Result<?> review(@PathVariable Long id,@PathVariable Long bookingId,@Valid @RequestBody BookingReview r){service.reviewBooking(id,bookingId,r.decision());return Result.success();}
    @GetMapping("/material-map") public Result<?> materialMap(){return Result.success(service.materialMap());}
    @GetMapping("/journey") public Result<?> journey(){return Result.success(service.journey());}
    @DeleteMapping("/records") public Result<?> erase(){service.erase();return Result.success();}
    @PutMapping("/mood") public Result<?> mood(@Valid @RequestBody GrowthRequests.Mood r){service.mood(r);return Result.success();}
    @GetMapping("/scales") public Result<?> scales(){return Result.success(scales.available());}
    @PostMapping("/assessment") public Result<?> assessment(@Valid @RequestBody GrowthRequests.Assessment r){return Result.success(service.assessment(r));}
    @PostMapping("/reflection") public Result<?> reflection(@Valid @RequestBody GrowthRequests.Reflection r){service.reflection(r);return Result.success();}
    @PostMapping("/reflection/summary") public Result<?> reflectionSummary(@Valid @RequestBody GrowthRequests.ReflectionSummary r){return Result.success(reflectionSummaryService.generate(r));}
    @GetMapping("/programs") public Result<?> programs(){return Result.success(service.programs(false));}
    @GetMapping("/programs/manage") public Result<?> manage(){return Result.success(service.programs(true));}
    @PostMapping("/programs") public Result<?> create(@Valid @RequestBody GrowthRequests.Program r){return Result.success(Map.of("id",service.program(null,r)));}
    @PutMapping("/programs/{id}") public Result<?> update(@PathVariable Long id,@Valid @RequestBody GrowthRequests.Program r){return Result.success(Map.of("id",service.program(id,r)));}
    @PostMapping("/programs/{id}/book") public Result<?> book(@PathVariable Long id){service.book(id,false);return Result.success();}
    @DeleteMapping("/programs/{id}/book") public Result<?> cancel(@PathVariable Long id){service.book(id,true);return Result.success();}
    @GetMapping("/programs/{id}/attendees") public Result<?> attendees(@PathVariable Long id){return Result.success(service.attendees(id));}
    @PostMapping("/programs/{id}/checkin") public Result<?> checkin(@PathVariable Long id,@Valid @RequestBody GrowthRequests.Checkin r){service.checkin(id,r.bookingId());return Result.success();}
    @GetMapping("/safety/{id}") public Result<?> safety(@PathVariable Long id){return Result.success(service.safety(id));}
    @PutMapping("/safety/{id}") public Result<?> safety(@PathVariable Long id,@Valid @RequestBody GrowthRequests.Safety r){service.safety(id,r);return Result.success();}
}
