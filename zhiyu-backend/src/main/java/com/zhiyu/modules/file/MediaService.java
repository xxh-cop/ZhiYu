package com.zhiyu.modules.file;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhiyu.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MediaService {
 private final JdbcTemplate db;private final MediaStorage storage;private final byte[] secret;
 private static final Pattern REF=Pattern.compile("/api/media/(\\d+)/content");
 public MediaService(JdbcTemplate db,MediaStorage storage,@Value("${zhiyu.jwt.secret}") String secret){this.db=db;this.storage=storage;this.secret=secret.getBytes(StandardCharsets.UTF_8);}
 public String upload(Long uid,MultipartFile file){
  requireUser(uid);
  if(file==null||file.isEmpty()||file.getSize()>50L*1024*1024)throw new BusinessException(1001,"请选择不超过 50MB 的 MP4 视频");
  if(!Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT).endsWith(".mp4"))throw new BusinessException(1001,"请先导出为 MP4（建议 H.264 + AAC）");
  if(db.queryForObject("SELECT COUNT(*) FROM media_asset WHERE user_id=? AND create_time>=?",Long.class,uid,LocalDate.now().atStartOfDay())>=20)throw new BusinessException(1001,"今天的视频上传次数已达 20 次，请明天再试");
  String key=UUID.randomUUID().toString().replace("-","")+".mp4";
  try(var in=file.getInputStream()){
   byte[] header=in.readNBytes(64);validateMp4(header);
  }catch(java.io.IOException e){throw new BusinessException(1001,"无法读取视频，请重新选择");}
  try(var in=file.getInputStream()){
   storage.put(key,in);long id=IdWorker.getId();var now=LocalDateTime.now();
   db.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted) VALUES(?,?,?,?,?,?,0)",id,uid,key,file.getSize(),now,now);return reference(id);
  }catch(Exception e){try{storage.remove(key);}catch(Exception ignored){}throw new BusinessException(5000,"视频保存失败，请重试");}
 }
 static void validateMp4(byte[] h){
  if(h.length<16||!new String(h,4,4,StandardCharsets.US_ASCII).equals("ftyp"))throw new BusinessException(1001,"文件内容不是有效 MP4");
  int length=ByteBuffer.wrap(h,0,4).getInt();String brand=new String(h,8,4,StandardCharsets.US_ASCII);
  if(length<16||length>4096||!Set.of("isom","iso2","mp41","mp42","avc1","M4V ","MSNV").contains(brand))throw new BusinessException(1001,"不支持此视频封装，请转换为常规 MP4");
 }
 public String requireOwned(Long uid,String ref){
  var match=REF.matcher(Objects.toString(ref,""));if(!match.matches())throw new BusinessException(1001,"请使用本平台上传的视频");
  if(db.queryForObject("SELECT COUNT(*) FROM media_asset WHERE id=? AND user_id=? AND deleted=0",Long.class,Long.parseLong(match.group(1)),uid)!=1)throw new BusinessException(1003,"无权使用此视频");return ref;
 }
 public String playback(Long uid,long id){authorize(uid,id);long expires=Instant.now().plusSeconds(3600).getEpochSecond();return reference(id)+"?uid="+uid+"&expires="+expires+"&signature="+sign(id,uid,expires);}
 public Resource content(long id,Long uid,long expires,String signature){
  long now=Instant.now().getEpochSecond();if(expires<now||expires>now+3605||signature==null||!MessageDigest.isEqual(sign(id,uid,expires).getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))throw new BusinessException(1003,"播放地址已失效，请重新加载");
  authorize(uid,id);String key=db.queryForObject("SELECT storage_key FROM media_asset WHERE id=? AND deleted=0",String.class,id);
  try{return storage.read(key);}catch(Exception e){throw new BusinessException(1004,"视频文件不存在");}
 }
 private void requireUser(Long uid){if(uid==null)throw new BusinessException(1002,"请先登录");if(db.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND status=1 AND deleted=0",Long.class,uid)!=1)throw new BusinessException(1003,"账户不可用");}
 private void authorize(Long uid,long id){
  requireUser(uid);var owners=db.queryForList("SELECT user_id FROM media_asset WHERE id=? AND deleted=0",Long.class,id);if(owners.isEmpty())throw new BusinessException(1004,"视频不存在");
  if(owners.get(0).equals(uid)||db.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,uid)==3)return;
  String ref=reference(id);
  if(db.queryForObject("SELECT COUNT(*) FROM work WHERE video_url=? AND deleted=0 AND status=1 AND visible=1",Long.class,ref)>0)return;
  if(db.queryForObject("SELECT COUNT(*) FROM craft_project WHERE video_url=? AND deleted=0 AND status=1",Long.class,ref)>0)return;
  throw new BusinessException(1003,"此视频尚未公开或已下架");
 }
 private String sign(long id,Long uid,long expires){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(("media:"+id+":"+uid+":"+expires).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static String reference(long id){return "/api/media/"+id+"/content";}
}
