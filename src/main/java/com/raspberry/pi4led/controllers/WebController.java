package com.raspberry.pi4led.controllers;

import com.raspberry.pi4led.models.wagonModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Controller
public class WebController {
    final StationController stationController = new StationController(State.WAITING, Control.SERVER, 3, "Сургутская");

    @GetMapping("/")
    public String greeting(Model model) throws InterruptedException {
        ArrayList<StationController> station = new ArrayList<StationController>();
        station.add(stationController);
        model.addAttribute("station", station);

        ArrayList<String> cities = new ArrayList<String>(Arrays.asList("Москва", "Казань", "Нижневартовск", "Воркута"));
        model.addAttribute("cities", cities);

        ArrayList<wagonModel> wagonList = new ArrayList<wagonModel>();
        for(int i=1; i!=stationController.getTrainCounter()+1; i++) {
            int way = (int) (Math.random() * 4);
            wagonList.add(new wagonModel(i, cities.get(way), way));
        }
        model.addAttribute("wagonList", wagonList);
        return "index";
    }

    @ResponseBody
    @GetMapping("/wait")
    public SseEmitter startSorting()  {
        SseEmitter emitter = new SseEmitter();
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
        if(stationController.getState() == State.WAITING) {
            stationController.setState(State.COMING);
            cachedThreadPool.execute(() -> {
                try {
                    emitter.send("inWaiting");
                    stationController.sendMessage(142); //moving to position for sorting
//                    while (StationController.convertReceived(stationController.getReceivedMessage()) != 398
//                            || StationController.convertReceived(stationController.getReceivedMessage()) != 400) {
//                        Thread.onSpinWait();
//                    }
                    if (StationController.convertReceived(stationController.getReceivedMessage()) == 206) {
                        eventBuilder.id("1").data(stationController.getTrainCounter()).build();
                        emitter.send(eventBuilder);
                    } else {
                        stationController.setState(State.READY);
                        eventBuilder.id("2").data("Ready to sort").build();
                        emitter.send(eventBuilder);
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return emitter;
    }

    private static final String[] MAPS = "Screenshot_1 Screenshot_2 Screenshot_3 Screenshot_4 Screenshot_5 Screenshot_6".split(" ");
    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //private final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(4);
    @GetMapping(path = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter getWords(@RequestParam(value = "order", defaultValue = "0") String order) {
        stationController.setState(State.SORTING);
        SseEmitter emitter = new SseEmitter();
        SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event();
        cachedThreadPool.execute(() -> {
            try {
                for(char way : order.toCharArray()) {
                    way += 1;
                    System.out.println("SEMAPHORE " + way);
                    stationController.sendMessage(160 + 2 * way); //message to change semaphores
                    stationController.sendMessage(130 + 2 * way); //message to change arrows
                    eventBuilder.id("1").data("Screenshot_" + way).build();
                    emitter.send(eventBuilder);
                    if (stationController.getErrorId() != 0) {
                        eventBuilder.id("3").data(stationController.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                        break;
                    }
                }
                if(stationController.getErrorId() == 0) {
                    eventBuilder.id("2").data("Done sorting").build();
                    emitter.send(eventBuilder);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping("/restart")
    public String restartSystem() {
        stationController.setErrorId(0);
        stationController.setState(State.WAITING);
        return "redirect:/";
    }
}