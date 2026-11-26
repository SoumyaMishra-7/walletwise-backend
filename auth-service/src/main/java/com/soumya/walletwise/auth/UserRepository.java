package com.soumya.walletwise.auth;
import org.springframework.data.mongodb.repository.MongoRepository; import java.util.*;
interface UserRepository extends MongoRepository<User,String>{ Optional<User> findByEmail(String email); boolean existsByEmail(String email); }
