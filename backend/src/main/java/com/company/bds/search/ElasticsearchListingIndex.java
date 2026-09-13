package com.company.bds.search;

import com.company.bds.listing.domain.model.ListingSearchCriteria;
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
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final HttpClient http;
    private final String base; private volatile boolean available;
    public ElasticsearchListingIndex(JdbcTemplate jdbc,ObjectMapper json,@Value("${spring.elasticsearch.uris:http://localhost:9200}") String base){this.jdbc=jdbc;this.json=json;this.base=base.split(",")[0];this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();}
    @PostConstruct void init(){try{request("PUT","/bds-listings",json.writeValueAsString(Map.of("mappings",Map.of("properties",Map.of("location",Map.of("type","geo_point"),"title",Map.of("type","text","analyzer","standard"),"description",Map.of("type","text","analyzer","standard"))))));available=true;sync();}catch(Exception ignored){available=false;}}
    @Scheduled(fixedDelayString="${app.search.sync-ms:60000}") public void sync(){try{List<Map<String,Object>> rows=jdbc.queryForList("SELECT l.id,r.title,r.description,r.purpose,r.property_type,r.price_vnd,r.area_m2,r.address_summary,r.public_latitude,r.public_longitude,l.is_verified_owner FROM listings l JOIN listing_revisions r ON r.id=l.public_revision_id WHERE l.status='ACTIVE'");for(var row:rows){Object lat=row.remove("public_latitude"),lng=row.remove("public_longitude");if(lat!=null&&lng!=null)row.put("location",Map.of("lat",lat,"lon",lng));request("PUT","/bds-listings/_doc/"+row.remove("id"),json.writeValueAsString(row));}available=true;}catch(Exception ignored){available=false;}}
    public Optional<List<UUID>> search(ListingSearchCriteria c,int page,int size){if(!available)return Optional.empty();try{List<Object> filters=new ArrayList<>();if(c.purpose()!=null)filters.add(Map.of("term",Map.of("purpose.keyword",c.purpose().name())));if(c.propertyType()!=null)filters.add(Map.of("term",Map.of("property_type.keyword",c.propertyType().name())));range(filters,"price_vnd",c.minPriceVnd(),c.maxPriceVnd());range(filters,"area_m2",c.minAreaM2(),c.maxAreaM2());if(c.minLat()!=null&&c.maxLat()!=null&&c.minLng()!=null&&c.maxLng()!=null)filters.add(Map.of("geo_bounding_box",Map.of("location",Map.of("top_left",Map.of("lat",c.maxLat(),"lon",c.minLng()),"bottom_right",Map.of("lat",c.minLat(),"lon",c.maxLng())))));Object must=(c.keyword()==null||c.keyword().isBlank())?Map.of("match_all",Map.of()):Map.of("multi_match",Map.of("query",c.keyword(),"fields",List.of("title^3","description","address_summary")));Map<String,Object> body=Map.of("from",page*size,"size",size,"query",Map.of("bool",Map.of("must",List.of(must),"filter",filters)));JsonNode root=json.readTree(request("POST","/bds-listings/_search",json.writeValueAsString(body)));List<UUID> ids=new ArrayList<>();root.path("hits").path("hits").forEach(h->ids.add(UUID.fromString(h.path("_id").asText())));return Optional.of(ids);}catch(Exception ex){available=false;return Optional.empty();}}
    private void range(List<Object> f,String name,Object min,Object max){if(min==null&&max==null)return;Map<String,Object> b=new HashMap<>();if(min!=null)b.put("gte",min);if(max!=null)b.put("lte",max);f.add(Map.of("range",Map.of(name,b)));}
    private String request(String method,String path,String body)throws Exception{HttpRequest req=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(4)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body)).build();HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString());if(r.statusCode()>=400&&r.statusCode()!=400)throw new IllegalStateException("Elasticsearch "+r.statusCode());return r.body();}
}
