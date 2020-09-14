package bfst20.presentation;

import bfst20.data.InterestPointData;
import bfst20.logic.AppController;
import bfst20.logic.controllers.KDTreeAPI;
import bfst20.logic.controllers.KDTreeController;
import bfst20.logic.controllers.LinePathAPI;
import bfst20.logic.controllers.OSMElementAPI;
import bfst20.logic.misc.OSMType;
import bfst20.logic.entities.*;
import bfst20.logic.kdtree.Rect;
import bfst20.logic.misc.Vehicle;
import bfst20.logic.routing.Edge;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;


import java.util.*;
import java.util.List;

import javafx.scene.transform.NonInvertibleTransformException;

public class View {
    private Map<OSMType, List<LinePath>> linePaths;

    private boolean isColorBlindMode = false;
    private Affine trans = new Affine();

    private AppController appController;
    private List<LinePath> coastlines;
    private List<LinePath> motorways;
    private Label mouseLocationLabel;
    private OSMElementAPI osmElementController;
    private LinePathAPI linePathController;
    private KDTreeAPI kdTreeController;
    private Point2D mousePosition;
    private Address searchAddress;


    private GraphicsContext gc;

    private Canvas canvas;

    private List<Edge> route = null;
    private double zoomLevel = 1.0;
    private double timesZoomed = 0.0;
    private double sliderValue = 0;
    private long secondSinceLastRepaint = 0;
    private double pixelWidth;

    public static class Builder {
        private Canvas canvas;
        private LinePathAPI linePathController;
        private OSMElementAPI osmElementController;
        private KDTreeAPI kdTreeController;
        private Label mouseLocationLabel;

        public Builder(Canvas canvas) {
            this.canvas = canvas;
        }

        public Builder withLinePathAPI(LinePathAPI linePathAPI) {
            this.linePathController = linePathAPI;

            return this;
        }

        public Builder withOSMElementAPI(OSMElementAPI osmElementAPI) {
            this.osmElementController = osmElementAPI;

            return this;
        }

        public Builder withKDTreeAPI(KDTreeAPI kdTreeAPI) {
            this.kdTreeController = kdTreeAPI;

            return this;
        }

        public Builder withMouseLocationLabel(Label mouseLocationLabel) {
            this.mouseLocationLabel = mouseLocationLabel;

            return this;
        }

        public View Build() {
            View view = new View();
            view.canvas = this.canvas;
            view.linePathController = this.linePathController;
            view.osmElementController = this.osmElementController;
            view.kdTreeController = this.kdTreeController;
            view.mouseLocationLabel = this.mouseLocationLabel;

            view.mousePosition = new Point2D(0, 0);
            view.appController = new AppController();

            view.gc = canvas.getGraphicsContext2D();
            view.gc.setFill(Color.LIGHTBLUE);
            view.gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

            return view;
        }
    }


    public void initialize(boolean isBinary) {
        trans = new Affine();
        linePaths = linePathController.fetchLinePathData();
        coastlines = linePathController.fetchCoastlines();

        Bounds bounds = osmElementController.fetchBoundsData();

        float minLon = bounds.getMinLon();
        float maxLon = bounds.getMaxLon();
        float minLat = bounds.getMinLat();
        float maxLat = bounds.getMaxLat();

        pan(-minLon, -minLat);
        zoom(canvas.getHeight() / (maxLon - minLon), (minLat - maxLat) / 2, 0, 1);


        repaint();

    }

    public void setMousePosition(Point2D mousePosition) {
        this.mousePosition = mousePosition;
    }

    public void repaint() {
        if (fps()) return;

        gc.setTransform(new Affine());
        gc.setFill(OSMType.getColor(OSMType.OCEAN, isColorBlindMode));

        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.strokeRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setTransform(trans);

        pixelWidth = 1 / Math.sqrt(Math.abs(trans.determinant()));

        int boxSize = (int) canvas.getWidth() + 50;

        Rect rect = createRect(boxSize);

        Point2D mouse = convertCoordinates(
                mousePosition.getX(),
                mousePosition.getY());

        for (LinePath path : coastlines) {
            drawLinePath(path, pixelWidth);
        }

        drawAllKDTreeTypes(rect, mouse);

        setClosetLinePathToMouse();

        Point2D mc1 = convertCoordinates((canvas.getWidth() / 2) - boxSize, (canvas.getHeight() / 2) - boxSize);
        Point2D mc2 = convertCoordinates((canvas.getWidth() / 2) + boxSize, (canvas.getHeight() / 2) + boxSize);

        gc.beginPath();
        gc.setStroke(Color.BLUE);
        gc.strokeRect(mc1.getX(), mc1.getY(), mc2.getX() - mc1.getX(), mc2.getY() - mc1.getY());
        gc.stroke();

        drawSearchLocation(searchAddress, pixelWidth);
        drawInterestPoints(pixelWidth);

        if (route != null) {
            drawPointer(pixelWidth, 30, route.get(0).getTarget().getLongitude(), route.get(0).getTarget().getLatitude(), "1");
            drawPointer(pixelWidth, 30, route.get(route.size() - 1).getSource().getLongitude(), route.get(route.size() - 1).getSource().getLatitude(), "2");

            for (Edge edge : route) {
                drawRoute(edge, pixelWidth);
            }
        }
    }


    private boolean fps() {
        Date date = new Date();

        if (secondSinceLastRepaint == 0) {
            secondSinceLastRepaint = date.getTime();
            return false;
        } else {
            if ((date.getTime() - 30) < secondSinceLastRepaint) {
                return true;
            }
        }
        secondSinceLastRepaint = date.getTime();

        return false;
    }


    private void drawAllKDTreeTypes(Rect rect, Point2D mouse) {
        OSMType[] drawableTypes = OSMType.drawables();

        for (OSMType type : drawableTypes) {
            drawKDTree(type, rect, pixelWidth, null);
        }

        OSMType[] highwayTypes = OSMType.highways();

        for (OSMType type : highwayTypes) {
            drawKDTree(type, rect, pixelWidth, mouse);
        }
    }

    private void drawKDTree(OSMType type, Rect rect, double lineWidth, Point2D point) {
        if (kdTreeController.fetchKDTree(type) != null) {
            for (LinePath linePath : kdTreeController.fetchKDTree(type).getElementsInRect(rect, trans.determinant(), point)) {

                drawLinePath(linePath, lineWidth);
                gc.fill();
            }
        }
    }

    private Rect createRect(int boxSize) {
        Point2D mc1 = convertCoordinates((canvas.getWidth() / 2) - boxSize, (canvas.getHeight() / 2) - boxSize);
        Point2D mc2 = convertCoordinates((canvas.getWidth() / 2) + boxSize, (canvas.getHeight() / 2) + boxSize);
        return new Rect((float) mc1.getY(), (float) mc2.getY(), (float) mc1.getX(), (float) mc2.getX());
    }

    private void drawInterestPoints(double lineWidth) {
        InterestPointData interestPointData = InterestPointData.getInstance();

        int i = 0;

        for (InterestPoint interestPoint : interestPointData.getAllInterestPoints()) {
            int bubbleSize = 30;

            drawPointer(lineWidth, bubbleSize, interestPoint.getLongitude(), interestPoint.getLatitude(), String.valueOf(i));
            i++;
        }
    }

    public void shortestPath(String sourceQuery, String targetQuery, Vehicle vehicle) {
        double distance = appController.initializeRouting(sourceQuery, targetQuery, vehicle);

        route = appController.fetchRouteData();

        repaint();
    }

    public void setSearchAddress(Address address) {
        this.searchAddress = address;

        repaint();
    }

    public void drawSearchLocation(Address address, double lineWidth) {
        if (address == null) return;
        int bubbleSize = 30;

        drawPointer(lineWidth, bubbleSize, address.getLon(), address.getLat(), "1");
    }

    private void drawPointer(double lineWidth, int bubbleSize, float lon, float lat, String id) {
        gc.beginPath();
        gc.setStroke(Color.RED);
        gc.setFill(Color.RED);

        gc.setFont(new Font("Arial", lineWidth * 20));
        gc.fillText(id, lon - (lineWidth * bubbleSize / 2) + (lineWidth * bubbleSize / 3), lat - (lineWidth * bubbleSize * 1.4) + (lineWidth * bubbleSize / 1.5));
        gc.fill();

        gc.strokeOval(lon - (lineWidth * bubbleSize / 2), lat - (lineWidth * bubbleSize * 1.4), lineWidth * bubbleSize, lineWidth * bubbleSize);
        gc.moveTo(lon - (lineWidth * bubbleSize / 2), lat - (lineWidth * bubbleSize));
        gc.lineTo(lon, lat);

        gc.moveTo(lon + (lineWidth * bubbleSize / 2), lat - (lineWidth * bubbleSize));
        gc.lineTo(lon, lat);
        gc.stroke();

    }

    private void drawRoute(Edge edge, double lineWidth) {
        gc.setLineWidth(lineWidth);
        gc.beginPath();
        gc.setStroke(OSMType.getColor(OSMType.ROUTING, false));

        traceEdge(edge, gc);
        gc.stroke();
    }

    private void traceEdge(Edge edge, GraphicsContext gc) {
        Node sourceNode = edge.getSource();
        Node targetNode = edge.getTarget();

        float[] coords = new float[]{sourceNode.getLongitude(), sourceNode.getLatitude(), targetNode.getLongitude(), targetNode.getLatitude()};
        gc.setStroke(OSMType.getColor(OSMType.ROUTING, false));
        gc.moveTo(coords[0], coords[1]);

        for (int i = 2; i <= coords.length; i += 2) {
            gc.lineTo(coords[i - 2], coords[i - 1]);
        }
    }


    private void drawLinePath(LinePath linePath, double lineWidth) {
        OSMType OSMType = linePath.getOSMType();
        gc.setStroke(OSMType.getColor(OSMType, isColorBlindMode));

        gc.setLineWidth(OSMType.getLineWidth(OSMType, lineWidth));
        gc.setFill(linePath.getFill() ? OSMType.getColor(OSMType, isColorBlindMode) : Color.TRANSPARENT);

        if (linePath.isMultipolygon()) {
            traceMultipolygon(linePath, gc);
        } else {
            trace(linePath, gc);
        }
    }

    private void traceMultipolygon(LinePath linePath, GraphicsContext gc) {
        gc.beginPath();
        gc.setFillRule(FillRule.EVEN_ODD);

        float[] coords = linePath.getCoords();
        gc.moveTo(coords[0], coords[1]);

        for (int i = 2; i <= coords.length; i += 2) {
            gc.lineTo(coords[i - 2], coords[i - 1]);
        }
        gc.stroke();

        if (OSMType.getFill(linePath.getOSMType())) {
            gc.fill();
        }
    }

    private void trace(LinePath linePath, GraphicsContext gc) {
        gc.beginPath();
        draw(linePath, gc);
        gc.stroke();

        if (OSMType.getFill(linePath.getOSMType())) {
            gc.fill();
        }
    }

    private void draw(LinePath linePath, GraphicsContext gc) {
        float[] coords = linePath.getCoords();
        gc.moveTo(coords[0], coords[1]);

        for (int i = 2; i <= coords.length; i += 2) {
            gc.lineTo(coords[i - 2], coords[i - 1]);
        }
    }

    //Converts raw coordinates to canvas coordinates.
    public Point2D convertCoordinates(double x, double y) {
        try {
            return trans.inverseTransform(x, y);
        } catch (NonInvertibleTransformException e) {
            return null;
        }
    }

    public void zoom(double factor, double x, double y, double deltaY) {
        scale(factor, x, y, deltaY);
        reduceZoomLevel();
        reduceTimesZoomed();
    }


    private void scale(double factor, double x, double y, double deltaY) {
        trans.prependScale(factor, factor, x, y);
        timesZoomed += deltaY / 40;
        repaint();
    }

    private void reduceZoomLevel() {
        if (zoomLevel > 2500) {
            zoomLevel = zoomLevel / 2517.0648374271736;
        }
    }

    private void reduceTimesZoomed() {
        if (timesZoomed > 126) {
            timesZoomed = 126;
        }
    }

    public void pan(double dx, double dy) {
        trans.prependTranslation(dx, dy);
        repaint();
    }

    public void changeToColorBlindMode(boolean isColorBlindMode) {
        this.isColorBlindMode = isColorBlindMode;
    }

    public void setMouseLocationView(Label mouseLocationLabel) {
        this.mouseLocationLabel = mouseLocationLabel;
    }

    public double getTimesZoomed() {
        return timesZoomed;
    }

    public void setSliderValue(double value) {
        sliderValue = value;
    }

    public double getSliderValue() {
        return sliderValue;
    }

    private void setClosetLinePathToMouse() {
        try {
            OSMType[] types = OSMType.highways();

            Map<OSMType, Double> dist = new HashMap<>();

            for (OSMType type : types) {
                if (appController.fetchAllKDTrees().get(type) != null) {
                    dist.put(type, appController.fetchKDTree(type).getClosetsLinePathToMouseDistance());
                }
            }

            double shortestDistance = Double.POSITIVE_INFINITY;
            OSMType shortestType = null;

            for (Map.Entry<OSMType, Double> entry : dist.entrySet()) {
                if (entry.getValue() < shortestDistance) {
                    shortestDistance = entry.getValue();
                    shortestType = entry.getKey();
                }
            }

            String name = appController.fetchKDTree(shortestType).getClosetsLinepathToMouse().getName();
            mouseLocationLabel.setText(name == null ? "Unknown way" : name);
        } catch (Exception e) {

        }
    }
}