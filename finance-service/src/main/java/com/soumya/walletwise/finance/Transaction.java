package com.soumya.walletwise.finance;
import org.springframework.data.annotation.Id;import org.springframework.data.mongodb.core.mapping.Document;import java.math.*;import java.time.*;
@Document("transactions") public class Transaction{@Id public String id;public String userId,type,category,description;public BigDecimal amount;public LocalDate date=LocalDate.now();public Transaction(){}}
