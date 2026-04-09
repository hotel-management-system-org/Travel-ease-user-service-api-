package com.travel_ease.hotel_system.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


@Service
public class JwtService {
    @Value("${public.key.string}")
    private String publicKeyString;

    public String getEmail(String token){
        try{
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyString);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = factory.generatePublic(keySpec);

            Jws<Claims> claimsJwt = Jwts.parserBuilder().setSigningKey(publicKey)
                    .build().parseClaimsJws(token);

            Claims body = claimsJwt.getBody();
            return body.get("email", String.class);
        }catch(Exception e){
            System.out.println("JWT ERROR " + e.getMessage());
            throw new RuntimeException();
        }
    }


}