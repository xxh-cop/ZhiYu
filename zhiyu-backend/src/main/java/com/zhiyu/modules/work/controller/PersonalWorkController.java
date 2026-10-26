package com.zhiyu.modules.work.controller;
import com.zhiyu.common.result.Result;
import com.zhiyu.modules.work.service.PersonalWorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/works")
public class PersonalWorkController {
    private final PersonalWorkService service;
    @DeleteMapping("/favorites/{id}") public Result<?> unfavorite(@PathVariable long id){service.unfavorite(id);return Result.success();}
    @GetMapping("/favorites") public Result<?> favorites(@RequestParam(defaultValue="1") int page){return Result.success(service.favorites(page));}
    @GetMapping("/mine") public Result<?> mine(@RequestParam(defaultValue="1") int page,@RequestParam(required=false) Integer status){return Result.success(service.mine(page,status));}
    @PostMapping("/{id}/withdraw") public Result<?> withdraw(@PathVariable long id){service.withdraw(id);return Result.success();}
}
