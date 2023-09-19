/**
 * Copyright 2019 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.mycompany.app;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureTableEditResult;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.data.ServiceGeodatabase;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.view.MapView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    private MapView mapView;

    private ServiceFeatureTable featureTable;

    private static final String SERVICE_LAYER_URL =
        "https://services1.arcgis.com/6677msI40mnLuuLr/arcgis/rest/services/PointsofRelaxing/FeatureServer/0";

    private enum placeType {
        Cafe,
        Nature,
        Park,
        Urban,
        Water;
    }

    public static void main(String[] args) {

        Application.launch(args);
    }

    @Override
    public void start(Stage stage) {

        // set the title and size of the stage and show it
        stage.setTitle("My Map App");
        stage.setWidth(800);
        stage.setHeight(700);
        stage.show();

        // create a JavaFX scene with a stack pane as the root node and add it to the scene
        StackPane stackPane = new StackPane();
        Scene scene = new Scene(stackPane);
        stage.setScene(scene);

        // Note: it is not best practice to store API keys in source code.
        // An API key is required to enable access to services, web maps, and web scenes hosted in ArcGIS Online.
        // If you haven't already, go to your developer dashboard to get your API key.
        // Please refer to https://developers.arcgis.com/java/get-started/ for more information
        String yourAPIKey = System.getProperty("apiKey");
        ArcGISRuntimeEnvironment.setApiKey(yourAPIKey);
        System.out.println("API key -" + yourAPIKey);
        ArcGISRuntimeEnvironment.setApiKey(yourAPIKey);

        // create a MapView to display the map and add it to the stack pane
        mapView = new MapView();
        stackPane.getChildren().add(mapView);

        // create an ArcGISMap with an imagery basemap
        ArcGISMap map = new ArcGISMap(BasemapStyle.ARCGIS_IMAGERY);

        // display the map by setting the map on the map view
        mapView.setMap(map);

        // create a service geodatabase from the service layer url and load it
        var serviceGeodatabase = new ServiceGeodatabase(SERVICE_LAYER_URL);

        // the done loading listener will run the enclosed code on a separate thread which is off the UI thread
        // this is important as we are waiting the a web service which might take a while to respond.  Delays in the
        // service responding can cause the UI to hang which will result in user frustration
        serviceGeodatabase.addDoneLoadingListener(() -> {

            // create service feature table from the service geodatabase's table first layer
            featureTable = serviceGeodatabase.getTable(0);

            // create a feature layer from table
            var featureLayer = new FeatureLayer(featureTable);

            // add the layer to the ArcGISMap
            map.getOperationalLayers().add(featureLayer);
        });
        serviceGeodatabase.loadAsync();

        mapView.setOnMouseClicked(event -> {
            // check that the primary mouse button was clicked
            if (event.isStillSincePress() && event.getButton() == MouseButton.PRIMARY) {
                // create a point from where the user clicked
                Point2D point = new Point2D(event.getX(), event.getY());

                // create a map point from a point
                Point mapPoint = mapView.screenToLocation(point);

                // for a wrapped around map, the point coordinates include the wrapped around value
                // for a service in projected coordinate system, this wrapped around value has to be normalized
                Point normalizedMapPoint = (Point) GeometryEngine.normalizeCentralMeridian(mapPoint);

                // add a new feature to the service feature table
                addFeature("Tasty coffee", "Worth going here!", placeType.Cafe, normalizedMapPoint, featureTable);
            }
        });

    }

    /**
     * Adds a new Feature to a ServiceFeatureTable and applies the changes to the
     * server.
     *
     * @param mapPoint location to add feature
     * @param featureTable service feature table to add feature
     */
    private void addFeature(String name, String description, placeType type, Point mapPoint, ServiceFeatureTable featureTable) {

        // create default attributes for the feature
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("Name", name);
        attributes.put("Description", description);
        attributes.put("Category", type.name());

        // creates a new feature using default attributes and point
        Feature feature = featureTable.createFeature(attributes, mapPoint);

        // check if feature can be added to feature table
        if (featureTable.canAdd()) {
            // add the new feature to the feature table and to server
            featureTable.addFeatureAsync(feature).addDoneListener(() -> applyEdits(featureTable));
        } else {
            displayMessage(null, "Cannot add a feature to this feature table");
        }
    }

    /**
     * Sends any edits on the ServiceFeatureTable to the server.
     *
     * @param featureTable service feature table
     */
    private void applyEdits(ServiceFeatureTable featureTable) {

        // apply the changes to the server
        ListenableFuture<List<FeatureTableEditResult>> editResult = featureTable.getServiceGeodatabase().applyEditsAsync();
        editResult.addDoneListener(() -> {
            try {
                List<FeatureTableEditResult> edits = editResult.get();
                // check if the server edit was successful
                if (edits != null && edits.size() > 0) {
                    var featureEditResult = edits.get(0).getEditResult().get(0);
                    if (!featureEditResult.hasCompletedWithErrors()) {
                        displayMessage(null, "Feature successfully added");
                    } else {
                        throw featureEditResult.getError();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                displayMessage("Exception applying edits on server", e.getCause().getMessage());
            }
        });
    }

    /**
     * Shows a message in an alert dialog.
     *
     * @param title title of alert
     * @param message message to display
     */
    private void displayMessage(String title, String message) {

        // The runLater method will put the enclosed code back onto the UI thread.
        // If you are writing UI code, then this needs to be on the UI thread otherwise
        // your application could crash!
        Platform.runLater(() -> {
            Alert dialog = new Alert(Alert.AlertType.INFORMATION);
            dialog.initOwner(mapView.getScene().getWindow());
            dialog.setHeaderText(title);
            dialog.setContentText(message);
            dialog.showAndWait();
        });
    }

    /**
     * Stops and releases all resources used in application.
     */
    @Override
    public void stop() {

        if (mapView != null) {
            mapView.dispose();
        }
    }
}
