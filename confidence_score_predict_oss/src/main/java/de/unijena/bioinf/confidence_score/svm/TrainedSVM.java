/*
 *
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.confidence_score.svm;

import de.unijena.bioinf.ChemistryBase.utils.FileUtils;
import org.jetbrains.annotations.NotNull;

import javax.json.*;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Created by martin on 27.06.18.
 */

//Trained SVM object containing all the feature weights, names and scaling information
public class TrainedSVM {
    public double[] weights;
    public String[] names;
    public SVMScales scales;
    public double[] probAB;
    public int score_shift;


    public TrainedSVM(SVMScales scales, double[] weights, String[] names) {
        this.weights = weights;
        this.scales = scales;
        this.names = names;
    }

    public TrainedSVM(String json) {
        import_parameters(json);
    }

    public TrainedSVM(JsonObject json) {
        import_parameters(json);
    }

    public TrainedSVM(File file) throws IOException {
        import_parameters(file);
    }


    public void exportAsJSON(File file) {


        //TODO: false score distribution in here?

        JsonObjectBuilder json = Json.createObjectBuilder();

        for (int i = 0; i < weights.length; i++) {

            JsonArrayBuilder json_array = Json.createArrayBuilder();

            json_array.add(names[i]);
            json_array.add(weights[i]);
            json_array.add(scales.medians[i]);
            json_array.add(scales.deviations[i]);
            json_array.add(scales.min_feature_values[i]);
            json_array.add(scales.max_feature_values[i]);


            json.add("feature " + i, json_array.build());

        }


        JsonArrayBuilder sigmoid_array = Json.createArrayBuilder();


        sigmoid_array.add(probAB[0]);
        sigmoid_array.add(probAB[1]);


        json.add("sigmoid", sigmoid_array.build());

        javax.json.JsonObject json_obj = json.build();

        try {


            JsonWriter write_json = Json.createWriter(FileUtils.getWriter(file));
            write_json.writeObject(json_obj);
            write_json.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void import_parameters(File file) throws IOException {
        import_parameters(FileUtils.getReader(file));
    }

    public void import_parameters(Reader reader) {
        JsonReader parse_json_marvin = Json.createReader(reader);
        JsonObject object_marvin = parse_json_marvin.readObject();
            parse_json_marvin.close();

        import_parameters(object_marvin);
    }

    public void import_parameters(@NotNull String json) {
        import_parameters(new StringReader(json));
    }


    private void import_parameters(JsonObject jsonObject) {
        names = new String[jsonObject.keySet().size()-1];
        weights = new double[names.length];

        double[] medians = new double[names.length];
        double[] devs = new double[names.length];
        double[] mins = new double[names.length];
        double[] maxs = new double[names.length];

        int counter = 0;

        for (String key : jsonObject.keySet()) {

            JsonArray curr = jsonObject.getJsonArray(key);

            if (key.contains("feature")) {


                names[counter] = curr.get(0).toString();
                weights[counter] = Double.parseDouble(curr.get(1).toString());
                medians[counter] = Double.parseDouble(curr.get(2).toString());
                devs[counter] = Double.parseDouble(curr.get(3).toString());
                mins[counter] = Double.parseDouble(curr.get(4).toString());
                maxs[counter] = Double.parseDouble(curr.get(5).toString());

                counter++;

            }

            if (key.contains("sigmoid")) {
                probAB = new double[2];

                probAB[0] = Double.parseDouble(curr.get(0).toString());
                probAB[1] = Double.parseDouble(curr.get(1).toString());


            }


        }

        scales = new SVMScales(medians, devs, mins, maxs);
    }


}
