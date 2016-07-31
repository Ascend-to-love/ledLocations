package com.ascension.ledlocations;

import java.io.*;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import static java.lang.Math.*;

/**
 * Created by akesich on 7/18/16.
 */
public class LedLocations {
    public static double spacing = 1000.0 / 30.0; // 30 per 1000 mm

    public static long totalPlaced = 0;

    public static BufferedWriter outWriter;

    public static void main(String[] args) {
        String csvFile = "LEDStripLocations.csv";
        BufferedReader br = null;
        String line;
        String cvsSplitBy = ",";

        try {
            File outFile = new File("led-locations.csv");
            if (!outFile.exists()) {
                outFile.createNewFile();
            }

            FileWriter fw = new FileWriter(outFile.getAbsoluteFile());
            outWriter = new BufferedWriter(fw);

            outWriter.write("section,right_left,subsection,front_back,strip_number,segment_id,x,y,z,led_number,arc_strip_num,arc_length\n");

            br = new BufferedReader(new FileReader(csvFile));
            br.readLine(); // throw away header line
            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] vars = line.split(cvsSplitBy);

                System.out.println("Segment name: " + vars[6]);

                if (vars[7].equals("Arc")) {
                    arcSegment(vars);
                    continue;
                }

                if (vars[11].equals("n/a")) {
                    lineSegment(
                            Double.parseDouble(vars[8]),
                            Double.parseDouble(vars[9]),
                            Double.parseDouble(vars[10]),
                            Double.parseDouble(vars[14]),
                            Double.parseDouble(vars[15]),
                            Double.parseDouble(vars[16]),
                            vars,
                            0
                    );
                } else {
                    int startIndex = lineSegment(
                            Double.parseDouble(vars[8]),
                            Double.parseDouble(vars[9]),
                            Double.parseDouble(vars[10]),
                            Double.parseDouble(vars[11]),
                            Double.parseDouble(vars[12]),
                            Double.parseDouble(vars[13]),
                            vars,
                            0
                    );
                    lineSegment(
                            Double.parseDouble(vars[11]),
                            Double.parseDouble(vars[12]),
                            Double.parseDouble(vars[13]),
                            Double.parseDouble(vars[14]),
                            Double.parseDouble(vars[15]),
                            Double.parseDouble(vars[16]),
                            vars,
                            startIndex
                    );

                }

            }

            System.out.printf("Total LEDs placed: %d\n", totalPlaced);
            outWriter.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static int lineSegment(double x1, double y1, double z1, double x2, double y2, double z2, String[] vars, int startIndex) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        int segmentLeds = startIndex;

        double length = sqrt(pow(dx, 2) + pow(dy, 2) + pow(dz, 2));

        System.out.printf("Length: %f\n", length);

        for (int i = 0; i * spacing < length; i++) {
            double frac = (i * spacing) / length;
            double x = x1 + frac * dx;
            double y = y1 + frac * dy;
            double z = z1 + frac * dz;

//            System.out.printf("%f, %f, %f\n", x, y, z);
            try {
                outWriter.write(String.format("%s,%s,%s,%s,%s,%s,%f,%f,%f,%d\n",
                        vars[0], vars[1], vars[2], vars[3] ,vars[4], vars[5],  x, y, z, segmentLeds));
            } catch (IOException e) {
                e.printStackTrace();
            }
            totalPlaced++;
            segmentLeds++;
        }

        System.out.printf("Leds in segment: %d\n", segmentLeds);
        return segmentLeds;

    }

    public static void arcSegment(String[] vars) {
        double cylRadius = 95.5 / 2;
        //Vector3D radialUp = Vector3D.PLUS_K.scalarMultiply(cylRadius);

        Vector3D start = new Vector3D(
                Double.parseDouble(vars[8]),
                Double.parseDouble(vars[9]),
                Double.parseDouble(vars[10])
        );

        Vector3D end = new Vector3D(
                Double.parseDouble(vars[14]),
                Double.parseDouble(vars[15]),
                Double.parseDouble(vars[16])
        );

        Vector3D center = new Vector3D(
                Double.parseDouble(vars[19]),
                Double.parseDouble(vars[21]),
                Double.parseDouble(vars[20])
        );

        double radius = Double.parseDouble(vars[18]);

        System.out.printf("start: %g %g %g\n", start.getX(), start.getY(), start.getZ());
        System.out.printf("end: %g %g %g\n", end.getX(), end.getY(), end.getZ());
        System.out.printf("center: %g %g %g\n", center.getX(), center.getY(), center.getZ());
        System.out.printf("radius: %g\n", radius);

        Vector3D r1 = new Vector3D(1, start, -1, center);
        r1 = r1.normalize();
        Vector3D r2 = new Vector3D(1, end, -1, center);
        r2 = r2.normalize();
        Vector3D normal = r1.crossProduct(r2).normalize();

        double angle = Vector3D.angle(r1, r2);

        // assuming that all arc are minor arcs, so if the parametrization takes us farther away
        // from the end point, we must be going the wrong way.
        double direction = 1;
        if (parameterizedArc(0.01, radius, r1, normal, center).distance(end) > start.distance(end)) {
            direction = -1;
        }

        for (int i = 0; i < 10; i++) {
            double curLength = 0.0;
            int numPlaced = 0;
            boolean firstPass = true;
            Vector3D previousPoint = Vector3D.POSITIVE_INFINITY;

            double phi = i * PI / 5; // i * 2Pi / 10

            for (double t = 0; abs(t) < angle; t += direction * 0.0001) {
                double epsilon = 0.000001;
                Vector3D arcPep1 = parameterizedArc(t - epsilon, radius, r1, normal, center);
                Vector3D arcPep2 = parameterizedArc(t + epsilon, radius, r1, normal, center);
                Vector3D tangent = arcPep2.add(-1, arcPep1).normalize();

                Vector3D arcP = parameterizedArc(t, radius, r1, normal, center);

                Vector3D arcRadialVec = new Vector3D(1, center, -1, arcP).normalize().scalarMultiply(cylRadius);
                Vector3D radialVec = rotateVector(arcRadialVec, tangent, phi);
                Vector3D curvePoint = arcP.add(radialVec);

                if (firstPass) {
                    firstPass = false;
                } else {
                    curLength += curvePoint.distance(previousPoint);

                    if (curLength > numPlaced * spacing) {
                        numPlaced++;
                        totalPlaced++;
//                    System.out.printf("%g %g %g\n", curvePoint.getX(), curvePoint.getY(), curvePoint.getZ());
                        try {
                            outWriter.write(String.format("%s,%s,%s,%s,%s,%s,%g,%g,%g,%d,%d,%g\n",
                                    vars[0], vars[1], vars[2], vars[3], vars[4], vars[5],
                                    curvePoint.getX(), curvePoint.getY(), curvePoint.getZ(),
                                    numPlaced, i, t*radius));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                previousPoint = curvePoint;
            }
        }

    }

    // http://demonstrations.wolfram.com/ParametricEquationOfACircleIn3D/
    private static Vector3D parameterizedArc(double t, double r, Vector3D u, Vector3D n, Vector3D c) {
        return u.scalarMultiply(cos(t) * r)
                    .add(n.crossProduct(u).scalarMultiply(sin(t) * r))
                    .add(c);
    }

    // https://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle
    private static Vector3D rotateVector(Vector3D vec, Vector3D axis, double th) {
        axis = axis.normalize();
        double ux = axis.getX();
        double uy = axis.getY();
        double uz = axis.getZ();

        double vx = vec.getX();
        double vy = vec.getY();
        double vz = vec.getZ();

        double x = (cos(th) + ux * ux * (1 - cos(th))) * vx
                + (ux * uy * (1 - cos(th)) - uz * sin(th)) * vy
                + (ux * uz * (1 - cos(th)) + uy * sin(th)) * vz;

        double y = (ux * uy * (1 - cos(th)) + uz * sin(th)) * vx
                + (cos(th) + uy * uy * (1 - cos(th))) * vy
                + (uy * uz * (1 - cos(th)) - ux * sin(th)) * vz;

        double z = (ux * uz * (1 - cos(th)) - uy * sin(th)) * vx
                + (uy * uz * (1 - cos(th)) + ux * sin(th)) * vy
                + (cos(th) + uz * uz * (1 - cos(th))) * vz;

        return new Vector3D(x, y, z);
    }

}
