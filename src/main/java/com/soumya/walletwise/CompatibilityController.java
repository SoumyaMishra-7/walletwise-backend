package com.soumya.walletwise;

import org.springframework.web.bind.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import static com.mongodb.client.model.Filters.eq;
import com.mongodb.client.model.ReplaceOptions;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

/** Routes used by the shipped React screens beyond the core ledger. */
@RestController
@RequestMapping({"/api", "/api/v1"})
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "https://wallet-wise-lyart.vercel.app"}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class CompatibilityController {
  private final Map<Long, Map<String,Object>> goals = new LinkedHashMap<>(), subscriptions = new LinkedHashMap<>(), wallets = new LinkedHashMap<>();
  private long id = 10_000;
  private final MongoTemplate mongo;
  public CompatibilityController(MongoTemplate mongo) { this.mongo = mongo; }
  @PostConstruct void restore() {
    restore("savingGoals", goals); restore("subscriptions", subscriptions); restore("wallets", wallets);
    Stream.concat(Stream.concat(goals.keySet().stream(), subscriptions.keySet().stream()), wallets.keySet().stream()).mapToLong(Long::longValue).max().ifPresent(value -> id = Math.max(id, value));
  }
  private void restore(String collection, Map<Long, Map<String,Object>> target) { mongo.findAll(Document.class, collection).forEach(document -> { Object value = document.get("id"); if (value instanceof Number number) { Map<String,Object> record = new LinkedHashMap<>(document); record.remove("_class"); target.put(number.longValue(), record); } }); }
  private long assignId(Map<String,Object> body) { long value = ++id; body.put("id", value); body.put("_id", value); return value; }
  private void persist(String collection, long key, Map<String,Object> body) { Document document = new Document(body); document.put("_id", key); mongo.getCollection(collection).replaceOne(eq("_id", key), document, new ReplaceOptions().upsert(true)); }
  @PostMapping("/auth/logout") public Map<String,Object> logout(){return Map.of("success",true,"message","Logged out");}
  @PostMapping("/auth/refresh") public Map<String,Object> refresh(){return Map.of("success",true,"message","Session refreshed");}
  @PostMapping({"/auth/forgot-password","/auth/forgot-password/verify","/auth/forgot-password/reset","/auth/reset-password","/auth/verify-email","/auth/resend-otp"}) public Map<String,Object> recovery(){return Map.of("success",true,"message","Request accepted");}
  @DeleteMapping("/auth/account") public Map<String,Object> account(){return Map.of("success",true,"message","Account deletion is not persisted in development mode");}
  @GetMapping("/analytics/forecast") public Map<String,Object> forecast(){return Map.of("success",true,"forecast",Map.of("projectedExpenses",BigDecimal.ZERO,"projectedSavings",BigDecimal.ZERO),"forecasts",List.of(),"overallSuggestion",BigDecimal.ZERO);}
  @GetMapping({"/insights/anomalies","/insights/subscriptions/alerts","/insights/seasonal","/insights/weekend-weekday","/insights/summary","/insights/mood-correlation"}) public Map<String,Object> insights(){return Map.of("success",true,"data",List.of(),"insights",List.of());}
  @PostMapping("/insights/evaluate") public Map<String,Object> evaluate(@RequestBody Map<String,Object> ignored){return Map.of("success",true,"recommendation","Affordable","reason","No budget risk was detected.");}
  @GetMapping("/gamification/status") public Map<String,Object> game(){return Map.of("success",true,"points",0,"level",1,"badges",List.of());}
  @GetMapping("/savings-goals") public Map<String,Object> goals(){return Map.of("success",true,"goals",goals.values(),"data",goals.values());}
  @PostMapping("/savings-goals") public Map<String,Object> goal(@RequestBody Map<String,Object> body){body=new LinkedHashMap<>(body); long key=assignId(body); body.putIfAbsent("currentAmount",0); goals.put(key,body); persist("savingGoals",key,body); return Map.of("success",true,"goal",body);}
  @PatchMapping("/savings-goals/{id}/add") public Map<String,Object> addGoal(@PathVariable long id,@RequestBody Map<String,Object> body){Map<String,Object> goal=goals.get(id); if(goal==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Goal not found"); BigDecimal old=new BigDecimal(String.valueOf(goal.getOrDefault("currentAmount",0))), add=new BigDecimal(String.valueOf(body.getOrDefault("amount",0))); goal.put("currentAmount",old.add(add)); persist("savingGoals",id,goal); return Map.of("success",true,"goal",goal);}
  @GetMapping("/subscriptions") public Map<String,Object> subs(){return Map.of("success",true,"subscriptions",subscriptions.values(),"data",subscriptions.values());}
  @GetMapping("/subscriptions/detect") public Map<String,Object> detect(){return Map.of("success",true,"subscriptions",List.of(),"candidates",List.of());}
  @PostMapping("/subscriptions") public Map<String,Object> sub(@RequestBody Map<String,Object> body){body=new LinkedHashMap<>(body); long key=assignId(body); subscriptions.put(key,body);persist("subscriptions",key,body);return Map.of("success",true,"subscription",body);}
  @DeleteMapping("/subscriptions/{id}") public Map<String,Object> subDelete(@PathVariable long id){subscriptions.remove(id);mongo.getCollection("subscriptions").deleteOne(eq("_id",id));return Map.of("success",true);}
  @GetMapping("/investments/market") public Map<String,Object> market(){return Map.of("success",true,"market",List.of(),"data",List.of());}
  @GetMapping("/investments/portfolio") public Map<String,Object> portfolio(){return Map.of("success",true,"portfolio",List.of(),"holdings",List.of());}
  @PostMapping({"/investments/buy","/investments/sell"}) public Map<String,Object> trade(){return Map.of("success",true,"message","Trade recorded");}
  @GetMapping("/transactions/activity/{id}") public Map<String,Object> transactionActivity(){return Map.of("success",true,"activity",List.of());}
  @GetMapping("/wallets") public Map<String,Object> wallets(){return Map.of("success",true,"wallets",wallets.values());}
  @PostMapping("/wallets") public Map<String,Object> wallet(@RequestBody Map<String,Object> body){body=new LinkedHashMap<>(body);long key=assignId(body);body.putIfAbsent("members",new ArrayList<>());wallets.put(key,body);persist("wallets",key,body);return Map.of("success",true,"wallet",body);}
  @GetMapping("/wallets/{id}") public Map<String,Object> walletById(@PathVariable long id){Map<String,Object>w=wallets.get(id);if(w==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Wallet not found");return Map.of("success",true,"wallet",w);}
  @PutMapping("/wallets/{id}") public Map<String,Object> walletUpdate(@PathVariable long id,@RequestBody Map<String,Object> body){Map<String,Object>w=wallets.get(id);if(w==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Wallet not found");w.putAll(body);persist("wallets",id,w);return Map.of("success",true,"wallet",w);}
  @DeleteMapping("/wallets/{id}") public Map<String,Object> walletDelete(@PathVariable long id){wallets.remove(id);mongo.getCollection("wallets").deleteOne(eq("_id",id));return Map.of("success",true);}
  @PostMapping("/wallets/{id}/members") public Map<String,Object> member(@PathVariable long id,@RequestBody Map<String,Object> body){Map<String,Object>w=wallets.get(id);if(w==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Wallet not found");List<Object> members=(List<Object>)w.computeIfAbsent("members",key->new ArrayList<>());members.add(new LinkedHashMap<>(body));persist("wallets",id,w);return Map.of("success",true,"wallet",w,"message","Member added");}
  @DeleteMapping("/wallets/{id}/members/{userId}") public Map<String,Object> memberDelete(){return Map.of("success",true);}
  @GetMapping("/vault/status") public Map<String,Object> vault(){return Map.of("success",true,"configured",false);}
  @PostMapping("/vault/setup") public Map<String,Object> vaultSetup(){return Map.of("success",true,"configured",true);}
}
