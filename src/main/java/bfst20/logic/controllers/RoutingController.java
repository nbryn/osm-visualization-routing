package bfst20.logic.controllers;

import bfst20.data.RoutingData;
import bfst20.logic.controllers.interfaces.RoutingAPI;
import bfst20.logic.entities.Address;
import bfst20.logic.entities.LinePath;
import bfst20.logic.misc.Vehicle;
import bfst20.logic.routing.Edge;
import bfst20.logic.routing.Graph;
import bfst20.logic.services.AddressService;
import bfst20.logic.services.RoutingService;

import java.util.List;
import java.util.Map;

public class RoutingController implements RoutingAPI {
    private RoutingService routingService;
    private AddressService addressService;
    private RoutingData routingData;

    public RoutingController(RoutingService routingService, AddressService addressService) {
        this.routingService = routingService;
        this.routingData = RoutingData.getInstance();
        this.addressService= addressService;
    }

    @Override
    public void buildRoutingGraph(List<LinePath> highways) {
        routingService.buildRoutingGraph(highways);
    }

    @Override
    public void saveGraph(Graph graph) {
        routingData.saveGraph(graph);
    }

    @Override
    public Graph fetchGraph() {
        return routingData.getGraph();
    }

    @Override
    public double initializeRouting(String sourceQuery, String targetQuery, Vehicle vehicle) {
        Address source = addressService.findAddress(sourceQuery);
        Address target = addressService.findAddress(targetQuery);

        Graph graph = routingData.getGraph();
        List<Edge> edges = graph.getEdges();

        return routingService.calculateShortestRoute(graph, edges, source, target, vehicle);
    }

    @Override
    public List<Edge> fetchRouteData() {
        return routingData.getRoute();
    }

    @Override
    public Map<String, Double> fetchRouteDirections() {
        return routingData.getRouteDirections();
    }

    @Override
    public void clearRouteInfoData() {
        routingData.clearData();
    }


}
