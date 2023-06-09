package com.raspberry.pi4led.controllers;

import com.raspberry.pi4led.models.Control;
import com.raspberry.pi4led.models.State;
import com.raspberry.pi4led.models.StationModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class WebController {
    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    final StationModel stationModel = new StationModel(State.WAITING, Control.FIELD, "Сургутская");


    @GetMapping("/")
    public String greeting(Model model) throws InterruptedException {
        while(stationModel.isBusy()) {
            Thread.onSpinWait();
        }
        stationModel.setTryingToLoadPage(true);
        model.addAttribute("station", stationModel);
        model.addAttribute("cities", stationModel.getCities());
        model.addAttribute("counters", stationModel.getCounters());
        model.addAttribute("wagonList", stationModel.getWagonList());
        stationModel.setTryingToLoadPage(false);
        stationModel.sendMessage(49);
        return "index";
    }

    @ResponseBody
    @GetMapping(path = "/wait", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter prepareForSorting()  {
        SseEmitter emitter = new SseEmitter(-1L);
        cachedThreadPool.execute(() -> {
            if(stationModel.getState() == State.WAITING) {
                stationModel.setState(State.COMING);
                //cached thread pool
                try {
                    stationModel.sendMessage(15); //moving to position for sorting
                    while (stationModel.convertReceived(stationModel.getReceivedMessage()) != 21) {
                        if (stationModel.convertReceived(stationModel.getReceivedMessage()) == 19) {
                            var eventBuilder = SseEmitter.event();
                            eventBuilder.id("1").data(stationModel.getCities().get(0));
                            emitter.send(eventBuilder);
                            stationModel.getReceivedMessage().clear();
                        }
                    }
                    var eventBuilder = SseEmitter.event();
                    stationModel.setState(State.READY);
                    eventBuilder.id("2").data("Ready to sort").build();
                    emitter.send(eventBuilder);

                    if (stationModel.getErrorId() != 0) {
                        eventBuilder = SseEmitter.event();
                        eventBuilder.id("3").data(stationModel.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        return emitter;
    }

    @GetMapping(path = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter startSorting(@RequestParam(value = "order", defaultValue = "0") String order) {
        SseEmitter emitter = new SseEmitter(-1L);
        cachedThreadPool.execute(() -> {
            stationModel.setState(State.SORTING);
            try {
                for(char way : order.toCharArray()) {
                    stationModel.setCurrentWay(Character.getNumericValue(way)+1);

                    var eventBuilder = SseEmitter.event();
                    eventBuilder.id("1").data(stationModel.getCurrentWay()).build();
                    emitter.send(eventBuilder);

                    int msgToSemaphore = 33 + (2 * stationModel.getCurrentWay());
                    int msgToArrows = 1 + (2 * stationModel.getCurrentWay());

                    if (stationModel.getErrorId() != 0) {
                        eventBuilder.id("4").data(stationModel.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                        break;
                    }
                    int msgToReceive = 65+(2 * stationModel.getCurrentWay());
                    stationModel.sendMessage(msgToSemaphore); //message to change semaphores
                    stationModel.sendMessage(msgToArrows); //message to change arrows
                    while(stationModel.convertReceived(stationModel.getReceivedMessage())!=msgToReceive) {
                        Thread.onSpinWait();
                    }
                    eventBuilder = SseEmitter.event();
                    eventBuilder.id("2").data(stationModel.getCurrentWay()).build();

                }
                stationModel.sendMessage(49);
                var eventBuilder = SseEmitter.event();
                stationModel.setState(State.SORTED);
                eventBuilder.id("2").data("Done sorting").build();
                emitter.send(eventBuilder);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping("/restart")
    public String restartSystem() {
        stationModel.setErrorId(0);
        stationModel.setState(State.WAITING);
        stationModel.setTrainCounter(0);
        stationModel.getWagonList().clear();
        for (int i =0; i!= stationModel.getCounters().size(); i++) {
            stationModel.getCounters().set(i, 0);
        }
        stationModel.setCurrentWay(8);
        stationModel.getThreadListener().start();
        return "redirect:/";
    }
}