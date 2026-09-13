package com.company.bds.verification.application;
import com.company.bds.shared.security.PiiProtectionService;
import com.company.bds.verification.domain.model.*;
import com.company.bds.verification.domain.port.UserKycPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.*;
@Service @Transactional
public class KycApplicationService {
 private final UserKycPersistencePort persistence; private final PiiProtectionService pii;
 public KycApplicationService(UserKycPersistencePort p,PiiProtectionService pii){this.persistence=p;this.pii=pii;}
 public UserKycProfile submitKyc(UUID userId,String rawIdNumber,String fullName,String dob,String address,String front,String back,String selfie){
  if(front==null||back==null||selfie==null||!front.startsWith("/api/v1/media/kyc/")||!back.startsWith("/api/v1/media/kyc/")||!selfie.startsWith("/api/v1/media/kyc/"))throw new IllegalArgumentException("Cần đủ ảnh CCCD mặt trước, mặt sau và ảnh chân dung riêng tư.");
  String hash=pii.blindIndex(rawIdNumber);Optional<UserKycProfile> existing=persistence.findByIdNumberLookupHash(hash);if(existing.isPresent()&&!existing.get().getUserId().equals(userId))throw new IllegalStateException("CCCD đã liên kết với tài khoản khác.");
  var protectedId=pii.protect(rawIdNumber);return persistence.save(new UserKycProfile(UUID.randomUUID(),userId,protectedId.encrypted(),protectedId.blindIndex(),fullName.trim(),dob,address,front,back,selfie,null,KycStatus.PENDING,null,Instant.now(),null));
 }
 public UserKycProfile approveKyc(UUID id){var p=find(id);p.approve(Instant.now());return persistence.save(p);}
 public UserKycProfile rejectKyc(UUID id,String reason){var p=find(id);p.reject(reason,Instant.now());return persistence.save(p);}
 @Transactional(readOnly=true) public Optional<UserKycProfile> getKycByUserId(UUID userId){return persistence.findByUserId(userId);}
 @Transactional(readOnly=true) public List<UserKycProfile> getQueue(KycStatus status,int page,int size){return status==null?persistence.findPage(page,size):persistence.findByStatus(status,page,size);}
 private UserKycProfile find(UUID id){return persistence.findById(id).orElseThrow(()->new IllegalArgumentException("Không tìm thấy hồ sơ eKYC."));}
}
