package com.soumya.walletwise.auth;
import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.mapping.Document; import java.util.*;
@Document("users") public class User { @Id public String id; public String name,email,passwordHash,role="USER"; public Date createdAt=new Date(); public User(){} }
