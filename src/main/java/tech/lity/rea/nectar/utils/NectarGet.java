package tech.lity.rea.nectar.utils;

import com.github.kevinsawicki.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Jeremy Laviole <laviole@rea.lity.tech>
 */
public class NectarGet {

    private static final String USER_AGENT = "Mozilla/5.0";
    public static String server = "http://localhost:4567";
    public final static String ERROR = "error";

    public static void main(String[] args) {

//        testMarkerBoard();
//        testCameraCalib();
    }

    public static void loadCalibration(String file, String output, String type) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("file", file);     // "data/calibration-AstraS-rgb.yaml");
        params.put("output", output); // "camera0:calibration1");
        params.put("type", type);     // "pd");

        HttpRequest request = HttpRequest.get(server + "/nectar/load_configuration", params, true);
        System.out.println("Req: "  + request.message() + " " + request.body() );
    }
    
    public static void service(String name, String action){
        HttpRequest request = HttpRequest.get(server + "/nectar/service/"+name+"/"+action); 
        System.out.println("Service Req: " + request.message());
    }

    public static void testCameraCalib() {
        // Load a calibration.
        Map<String, String> params = new HashMap<String, String>();
        params.put("file", "data/calibration-AstraS-rgb.yaml");
        params.put("output", "camera0:calibration1");
        params.put("type", "pd");
//
        HttpRequest request = HttpRequest.get(server + "/nectar/load_configuration", params, true);
        System.out.println("Req: " + request);
        request.receive(System.out);
    }

    public static void testMarkerBoard() {
        // Load a calibration.
        Map<String, String> params = new HashMap<String, String>();
        params.put("file", "data/calib1.svg");
        params.put("output", "calib1");
        params.put("type", "mb");

        HttpRequest request = HttpRequest.get(server + "/nectar/load_configuration", params, true);
        System.out.println("Req: " + request);
        request.receive(System.out);
    }

//    public static String get(String url, Map<String, String> parameters) {
//
//        try {
//            URL obj = new URL(server + url);
//            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
//
//            //Request header
//            con.setRequestProperty("User-Agent", USER_AGENT);
//            con.setRequestMethod("GET");
//            con.setRequestProperty("Accept-Charset", "UTF-8");
//
//            if (parameters != null) {
//                con.setDoOutput(true);
//                DataOutputStream out = new DataOutputStream(con.getOutputStream());
//                out.writeBytes(ParameterStringBuilder.getParamsString(parameters));
//                out.flush();
//                out.close();
//            }
//
//            int responseCode = con.getResponseCode();
//            System.out.println("\nSending 'GET' request to URL : " + url);
//            System.out.println("Response Code : " + responseCode);
//
//            BufferedReader in = new BufferedReader(
//                    new InputStreamReader(con.getInputStream()));
//            String inputLine;
//            StringBuilder response = new StringBuilder();
//
//            while ((inputLine = in.readLine()) != null) {
//                response.append(inputLine);
//            }
//            in.close();
//
//            return response.toString();
//        } catch (MalformedURLException ex) {
//            Logger.getLogger(NectarGet.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (IOException ex) {
//            Logger.getLogger(NectarGet.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return ERROR;
//    }
////    https://www.baeldung.com/java-http-request
//
//    public static class ParameterStringBuilder {
//
//        public static String getParamsString(Map<String, String> params)
//                throws UnsupportedEncodingException {
//            StringBuilder result = new StringBuilder();
//
//            for (Map.Entry<String, String> entry : params.entrySet()) {
//                result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
//                result.append("=");
//                result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
//                result.append("&");
//            }
//
//            String resultString = result.toString();
//            return resultString.length() > 0
//                    ? resultString.substring(0, resultString.length() - 1)
//                    : resultString;
//        }
//    }
}
