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
 public java.util.List<java.util.Map<String,Object>> mine(Long uid,Long before){requireUser(uid);var rows=db.queryForList("SELECT id,size_bytes,create_time,storage_key FROM media_asset WHERE user_id=? AND deleted=0 AND id<? ORDER BY id DESC LIMIT 50",uid,before==null?Long.MAX_VALUE:before);for(var row:rows){row.put("id",row.get("id").toString());row.put("kind",row.remove("storage_key").toString().endsWith(".mp4")?"video":"image");}return rows;}
 public String contentType(long id){String key=db.queryForObject("SELECT storage_key FROM media_asset WHERE id=? AND deleted=0",String.class,id);if(key.endsWith(".mp3"))return "audio/mpeg";if(key.endsWith(".m4a"))return "audio/mp4";if(key.endsWith(".wav"))return "audio/wav";if(key.endsWith(".ogg"))return "audio/ogg";return key.endsWith(".mp4")?"video/mp4":key.endsWith(".png")?"image/png":key.endsWith(".webp")?"image/webp":"image/jpeg";}
 public String imageUrl(Long uid,String ref){if(ref!=null&&REF.matcher(ref).matches())return playback(uid,Long.parseLong(REF.matcher(ref).replaceAll("$1")));return ref;}
 /**
  * AU3（2026-11-04）：通用重写——ref 是本站私有媒体引用（/api/media/{id}/content）则换 1h 签名播放地址，
  * 否则（外链 / 前端包静态资源 /static/ 等）原样返回。授权不通过（如媒体已随注销清理）时降级为 null
  * 并记 warn——调用方是列表/详情装配，单个失效音频不应拖垮整个接口；前端拿到 null 隐藏播放入口。
  */
 public String playbackIfMediaRef(Long uid,String ref){
  if(ref==null||!REF.matcher(ref).matches())return ref;
  try{return playback(uid,Long.parseLong(REF.matcher(ref).replaceAll("$1")));}
  catch(BusinessException e){org.slf4j.LoggerFactory.getLogger(MediaService.class).warn("私有媒体引用已失效，下发降级为空: ref={}, uid={}, code={}",ref,uid,e.getCode());return null;}
 }
 public String uploadImage(Long uid,MultipartFile file){requireUser(uid);if(file==null||file.isEmpty()||file.getSize()>5L*1024*1024)throw new BusinessException(1001,"请选择不超过 5MB 的图片");if(db.queryForObject("SELECT COUNT(*) FROM media_asset WHERE user_id=? AND create_time>=?",Long.class,uid,LocalDate.now().atStartOfDay())>=50)throw new BusinessException(1001,"今天的媒体上传次数已达上限");String key=null;try{byte[] bytes=file.getBytes();String ext;if(bytes.length>=8&&bytes[0]==(byte)0x89&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71)ext="png";else if(bytes.length>=3&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xd8&&bytes[2]==(byte)0xff)ext="jpg";else if(bytes.length>=12&&new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP"))ext="webp";else throw new BusinessException(1001,"仅支持真实的 JPG、PNG 或 WebP 图片");key=UUID.randomUUID().toString().replace("-","")+"."+ext;storage.put(key,new java.io.ByteArrayInputStream(bytes));long id=IdWorker.getId();db.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,uid,key,file.getSize());return reference(id);}catch(BusinessException e){throw e;}catch(Exception e){if(key!=null)try{storage.remove(key);}catch(Exception ignored){}throw new BusinessException(5000,"图片保存失败，请重试");}}
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
 /**
  * AU3（2026-11-04）：步骤引导音频上传。只放给传承人（role=1）与管理员（role=3）——
  * 学生是播放受众而非上传者。魔数嗅探（不认扩展名）：mp3(ID3/帧同步)/wav(RIFF+WAVE)/ogg(OggS)/m4a(ftyp)。
  * 上限 10MB（128kbps 约 1MB/分钟，覆盖 ~10 分钟引导语）；每日 20 次（与视频同级的较大资产口径）。
  * 失败语义同 upload：存储写成功后落库失败会清理已写对象，不留半成品，客户端可直接重试。
  */
 public String uploadAudio(Long uid,MultipartFile file){
  requireUser(uid);
  int role=db.queryForObject("SELECT role FROM user WHERE id=?",Integer.class,uid);
  if(role!=1&&role!=3)throw new BusinessException(1003,"仅传承人与管理员可上传引导音频");
  if(file==null||file.isEmpty()||file.getSize()>10L*1024*1024)throw new BusinessException(1001,"请选择不超过 10MB 的音频（MP3/WAV/M4A/OGG）");
  if(db.queryForObject("SELECT COUNT(*) FROM media_asset WHERE user_id=? AND create_time>=?",Long.class,uid,LocalDate.now().atStartOfDay())>=20)throw new BusinessException(1001,"今天的音频上传次数已达 20 次，请明天再试");
  String key=null;
  try{
   byte[] bytes=file.getBytes();String ext=sniffAudio(bytes);
   key=UUID.randomUUID().toString().replace("-","")+"."+ext;
   storage.put(key,new java.io.ByteArrayInputStream(bytes));
   long id=IdWorker.getId();
   db.update("INSERT INTO media_asset(id,user_id,storage_key,size_bytes,create_time,update_time,deleted) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",id,uid,key,bytes.length);
   return reference(id);
  }catch(BusinessException e){throw e;}catch(Exception e){if(key!=null)try{storage.remove(key);}catch(Exception ignored){}throw new BusinessException(5000,"音频保存失败，请重试");}
 }
 static String sniffAudio(byte[] b){
  if(b.length>=3&&b[0]=='I'&&b[1]=='D'&&b[2]=='3')return "mp3";
  if(b.length>=2&&(b[0]&0xFF)==0xFF&&(b[1]&0xE0)==0xE0)return "mp3";
  if(b.length>=12&&new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,StandardCharsets.US_ASCII).equals("WAVE"))return "wav";
  if(b.length>=4&&new String(b,0,4,StandardCharsets.US_ASCII).equals("OggS"))return "ogg";
  if(b.length>=12&&new String(b,4,4,StandardCharsets.US_ASCII).equals("ftyp"))return "m4a";
  throw new BusinessException(1001,"仅支持真实的 MP3、WAV、M4A 或 OGG 音频");
 }
 /**
  * AU3/AY2：孤儿媒体退役——ref 不再被任何未删除对象（步骤音频 / 项目视频 / 项目封面 /
  * 作品视频 / 作品封面）引用时，逻辑注销媒体并登记存储清理。
  * 幂等、可重复调用；非本站媒体引用（外链/静态资源）直接忽略。物理删除由 FileCleanupWorker 异步重试。
  */
 public void retireOrphanedMedia(String ref){
  var m=REF.matcher(Objects.toString(ref,""));if(!m.matches())return;
  long id=Long.parseLong(m.group(1));
  Long refs=db.queryForObject("SELECT (SELECT COUNT(*) FROM craft_step WHERE audio_url=? AND deleted=0)"
          + "+(SELECT COUNT(*) FROM craft_project WHERE (video_url=? OR cover_url=?) AND deleted=0)"
          + "+(SELECT COUNT(*) FROM work WHERE (video_url=? OR cover_url=?) AND deleted=0)",Long.class,ref,ref,ref,ref,ref);
  if(refs!=null&&refs>0)return;
  var rows=db.queryForList("SELECT storage_key FROM media_asset WHERE id=? AND deleted=0",id);
  if(rows.isEmpty())return;
  markDeletedAndScheduleCleanup(id,rows.get(0).get("storage_key").toString());
 }
 /**
  * 本人删除自己上传的媒体（2026-11-05 健壮性批，DELETE /api/media/{id}）。
  * 仅未引用媒体可删：引用计数口径与 {@link #retireOrphanedMedia} 完全一致
  *（步骤音频 / 项目视频封面 / 作品视频封面）；被引用时不得物理删除，业务拒绝 3001。
  * 删除语义与退役一致：逻辑注销 + file_cleanup_job 登记，物理删除由 FileCleanupWorker 异步重试。
  */
 public void deleteOwned(Long uid,long id){
  requireUser(uid);
  var rows=db.queryForList("SELECT user_id,storage_key FROM media_asset WHERE id=? AND deleted=0",id);
  if(rows.isEmpty())throw new BusinessException(1004,"媒体不存在");
  if(((Number)rows.get(0).get("user_id")).longValue()!=uid.longValue())throw new BusinessException(1003,"只能删除本人上传的媒体");
  String ref=reference(id);
  Long refs=db.queryForObject("SELECT (SELECT COUNT(*) FROM craft_step WHERE audio_url=? AND deleted=0)"
          + "+(SELECT COUNT(*) FROM craft_project WHERE (video_url=? OR cover_url=?) AND deleted=0)"
          + "+(SELECT COUNT(*) FROM work WHERE (video_url=? OR cover_url=?) AND deleted=0)",Long.class,ref,ref,ref,ref,ref);
  if(refs!=null&&refs>0)throw new BusinessException(3001,"该媒体正被作品或项目引用，请先解除引用后再删除");
  markDeletedAndScheduleCleanup(id,rows.get(0).get("storage_key").toString());
 }
 /** 逻辑注销媒体并登记 PRIVATE 清理任务（退役与本人删除共用；幂等，物理删除异步重试）。 */
 private void markDeletedAndScheduleCleanup(long id,String key){
  db.update("UPDATE media_asset SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
  if(db.queryForObject("SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE' AND storage_key=?",Integer.class,key)==0)
   db.update("INSERT INTO file_cleanup_job(id,storage_kind,storage_key) VALUES(?,'PRIVATE',?)",IdWorker.getId(),key);
 }
 public String requireOwned(Long uid,String ref){
  var match=REF.matcher(Objects.toString(ref,""));if(!match.matches())throw new BusinessException(1001,"请使用本平台上传的媒体文件");
  if(db.queryForObject("SELECT COUNT(*) FROM media_asset WHERE id=? AND user_id=? AND deleted=0",Long.class,Long.parseLong(match.group(1)),uid)!=1)throw new BusinessException(1003,"无权使用此媒体文件");return ref;
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
  if(db.queryForObject("SELECT COUNT(*) FROM work WHERE (video_url=? OR cover_url=?) AND deleted=0 AND status=1 AND visible=1",Long.class,ref,ref)>0)return;
  if(db.queryForObject("SELECT COUNT(*) FROM craft_project WHERE video_url=? AND deleted=0 AND status=1",Long.class,ref)>0)return;
  if(db.queryForObject("SELECT COUNT(*) FROM work w JOIN group_work g ON g.work_id=w.id AND g.deleted=0 JOIN group_member m ON m.group_id=g.group_id AND m.deleted=0 JOIN support_group s ON s.id=g.group_id AND s.deleted=0 AND s.status=1 WHERE (w.video_url=? OR w.cover_url=?) AND w.deleted=0 AND w.status=1 AND w.visible=2 AND m.user_id=?",Long.class,ref,ref,uid)>0)return;
  // AU3：已发布项目的步骤引导音频，登录用户均可按受众授权播放
  if(db.queryForObject("SELECT COUNT(*) FROM craft_step s JOIN craft_project p ON p.id=s.project_id AND p.deleted=0 AND p.status=1 WHERE s.audio_url=? AND s.deleted=0",Long.class,ref)>0)return;
  throw new BusinessException(1003,"此媒体尚未公开或已下架");
 }
 private String sign(long id,Long uid,long expires){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(("media:"+id+":"+uid+":"+expires).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static String reference(long id){return "/api/media/"+id+"/content";}
}
