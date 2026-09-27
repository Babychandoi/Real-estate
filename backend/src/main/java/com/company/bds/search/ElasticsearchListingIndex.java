package com.company.bds.search;

import com.company.bds.listing.domain.model.ListingSearchCriteria;
import com.company.bds.shared.scheduling.ScheduledTaskLock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class ElasticsearchListingIndex {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final HttpClient http; private final ScheduledTaskLock taskLock;
    private final String base; private final boolean enabled; private final String index; private final boolean syncOnStartup; private final Duration syncMinHold; private volatile boolean available;
    public ElasticsearchListingIndex(JdbcTemplate jdbc,ObjectMapper json,ScheduledTaskLock taskLock,@Value("${spring.elasticsearch.uris:http://localhost:9200}") String base,
                                     @Value("${app.search.elasticsearch.enabled:true}") boolean enabled,
                                     @Value("${app.search.index-name:bds-listings}") String index,
                                     @Value("${app.search.sync-on-startup:true}") boolean syncOnStartup,
                                     @Value("${app.search.sync-ms:60000}") long syncMs){this.jdbc=jdbc;this.json=json;this.taskLock=taskLock;this.base=base.split(",")[0];this.enabled=enabled;this.index=index;this.syncOnStartup=syncOnStartup;this.syncMinHold=Duration.ofMillis(Math.min(syncMs*9/10,Duration.ofMinutes(5).toMillis()));this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();}
    @PostConstruct void init(){if(!enabled){available=false;return;}try{requestAllowIndexExists("PUT","/"+index,json.writeValueAsString(Map.of("mappings",Map.of("properties",Map.of("listing_id",Map.of("type","keyword"),"location",Map.of("type","geo_point"),"title",Map.of("type","text","analyzer","standard"),"description",Map.of("type","text","analyzer","standard"))))));available=true;if(syncOnStartup)sync();}catch(Exception ignored){available=false;}}
    /**
     * Full re-sync (replaced by the incremental pipeline of S2). Only the instance holding the task lock runs it, and the lock
     * is held for 90 % of the interval so N instances still sync once per interval, not N times; the others only refresh
     * their availability flag. The first scheduled run is one interval after startup ({@link #init} already synced).
     */
    @Scheduled(fixedDelayString="${app.search.sync-ms:60000}",initialDelayString="${app.search.sync-ms:60000}") public void sync(){if(!enabled)return;if(!taskLock.runExclusive("search-index-sync",Duration.ofMinutes(5),syncMinHold,this::syncAll))available=probe();}
    private boolean probe(){try{return send("GET","/"+index+"/_count","").statusCode()<400;}catch(Exception ex){return false;}}
    private void syncAll(){try{List<Map<String,Object>> rows=jdbc.queryForList("SELECT l.id,l.created_at,r.title,r.description,r.purpose,r.property_type,r.price_vnd,r.area_m2,r.address_summary,r.public_latitude,r.public_longitude,l.is_verified_owner FROM listings l JOIN listing_revisions r ON r.id=l.public_revision_id WHERE l.status='ACTIVE'");for(var row:rows){Object lat=row.remove("public_latitude"),lng=row.remove("public_longitude");Object id=row.remove("id");row.put("listing_id",id.toString());if(lat!=null&&lng!=null)row.put("location",Map.of("lat",lat,"lon",lng));request("PUT","/"+index+"/_doc/"+id,json.writeValueAsString(row));}available=true;}catch(Exception ignored){available=false;}}
    public Optional<List<UUID>> search(ListingSearchCriteria c,int page,int size){if(!enabled||!available)return Optional.empty();try{List<Object> filters=new ArrayList<>();if(c.purpose()!=null)filters.add(Map.of("term",Map.of("purpose.keyword",c.purpose().name())));if(c.propertyType()!=null)filters.add(Map.of("term",Map.of("property_type.keyword",c.propertyType().name())));range(filters,"price_vnd",c.minPriceVnd(),c.maxPriceVnd());range(filters,"area_m2",c.minAreaM2(),c.maxAreaM2());if(c.minLat()!=null&&c.maxLat()!=null&&c.minLng()!=null&&c.maxLng()!=null)filters.add(Map.of("geo_bounding_box",Map.of("location",Map.of("top_left",Map.of("lat",c.maxLat(),"lon",c.minLng()),"bottom_right",Map.of("lat",c.minLat(),"lon",c.maxLng())))));Object must=(c.keyword()==null||c.keyword().isBlank())?Map.of("match_all",Map.of()):Map.of("multi_match",Map.of("query",c.keyword(),"fields",List.of("title^3","description","address_summary")));Map<String,Object> body=new LinkedHashMap<>();body.put("from",page*size);body.put("size",size);body.put("query",Map.of("bool",Map.of("must",List.of(must),"filter",filters)));body.put("sort",sort(c.sortBy()));JsonNode root=json.readTree(request("POST","/"+index+"/_search",json.writeValueAsString(body)));List<UUID> ids=new ArrayList<>();root.path("hits").path("hits").forEach(h->ids.add(UUID.fromString(h.path("_id").asText())));return Optional.of(ids);}catch(Exception ex){available=false;return Optional.empty();}}
    private List<Object> sort(String sortBy){return switch(sortBy==null?"LATEST":sortBy){case "PRICE_ASC"->List.of(Map.of("price_vnd",Map.of("order","asc")),Map.of("listing_id",Map.of("order","desc")));case "PRICE_DESC"->List.of(Map.of("price_vnd",Map.of("order","desc")),Map.of("listing_id",Map.of("order","desc")));case "AREA_DESC"->List.of(Map.of("area_m2",Map.of("order","desc")),Map.of("listing_id",Map.of("order","desc")));default->List.of(Map.of("created_at",Map.of("order","desc")),Map.of("listing_id",Map.of("order","desc")));};}
    private void range(List<Object> f,String name,Object min,Object max){if(min==null&&max==null)return;Map<String,Object> b=new HashMap<>();if(min!=null)b.put("gte",min);if(max!=null)b.put("lte",max);f.add(Map.of("range",Map.of(name,b)));}
    private String request(String method,String path,String body)throws Exception{HttpResponse<String> r=send(method,path,body);if(r.statusCode()>=400)throw new IllegalStateException("Elasticsearch "+r.statusCode()+": "+r.body());return r.body();}
    private String requestAllowIndexExists(String method,String path,String body)throws Exception{HttpResponse<String> r=send(method,path,body);if(r.statusCode()>=400&&!(r.statusCode()==400&&r.body().contains("resource_already_exists_exception")))throw new IllegalStateException("Elasticsearch "+r.statusCode()+": "+r.body());return r.body();}
    private HttpResponse<String> send(String method,String path,String body)throws Exception{HttpRequest req=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(4)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body)).build();return http.send(req,HttpResponse.BodyHandlers.ofString());}
}
