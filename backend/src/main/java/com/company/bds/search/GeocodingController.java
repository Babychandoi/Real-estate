package com.company.bds.search;

import com.company.bds.iam.application.AuthService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.net.URI; import java.net.URLEncoder; import java.net.http.*; import java.nio.charset.StandardCharsets; import java.time.Duration;

@RestController @RequestMapping("/api/v1/public/geocoding")
public class GeocodingController {
 private final JdbcTemplate jdbc; private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(); private long lastRequest;
 public GeocodingController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping public synchronized String search(@RequestParam String q)throws Exception{
   String query=q.trim();if(query.length()<3||query.length()>250)throw new IllegalArgumentException("Địa chỉ phải dài từ 3 đến 250 ký tự.");String hash=AuthService.sha256(query.toLowerCase());
   var cached=jdbc.queryForList("SELECT response_json FROM geocode_cache WHERE query_hash=?",String.class,hash);if(!cached.isEmpty())return cached.get(0);
   long wait=1000-(System.currentTimeMillis()-lastRequest);if(wait>0)Thread.sleep(wait);lastRequest=System.currentTimeMillis();
   var req=HttpRequest.newBuilder(URI.create("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=vn&q="+URLEncoder.encode(query,StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(8)).header("User-Agent","BDS-Trusted-Listings/1.0 admin@example.invalid").GET().build();
   var res=http.send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()!=200)throw new IllegalStateException("Dịch vụ định vị đang bận.");
   jdbc.update("INSERT INTO geocode_cache(query_hash,query_text,response_json) VALUES(?,?,?) ON CONFLICT(query_hash) DO NOTHING",hash,query,res.body());return res.body();
 }
}
