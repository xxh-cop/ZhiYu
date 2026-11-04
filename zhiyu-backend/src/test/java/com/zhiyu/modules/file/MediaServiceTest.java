package com.zhiyu.modules.file;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ByteArrayResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MediaServiceTest {
 JdbcTemplate db;MediaService service;MediaStorage storage;
 @BeforeEach void setup(){db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:media"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1","sa",""));db.execute("CREATE TABLE user(id BIGINT PRIMARY KEY,role INT,status INT,deleted INT)");db.execute("INSERT INTO user VALUES(1,0,1,0),(2,0,1,0),(3,3,1,0),(4,1,1,0)");db.execute("CREATE TABLE media_asset(id BIGINT PRIMARY KEY,user_id BIGINT,storage_key VARCHAR(180),size_bytes BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,deleted INT)");db.execute("CREATE TABLE work(video_url VARCHAR(255),status INT,visible INT,deleted INT,id BIGINT,cover_url VARCHAR(255))");db.execute("CREATE TABLE craft_project(id BIGINT,video_url VARCHAR(255),cover_url VARCHAR(255),status INT,deleted INT)");db.execute("CREATE TABLE craft_step(id BIGINT PRIMARY KEY,project_id BIGINT,audio_url VARCHAR(255),deleted INT)");db.execute("CREATE TABLE file_cleanup_job(id BIGINT PRIMARY KEY,storage_kind VARCHAR(16),storage_key VARCHAR(255))");db.execute("CREATE TABLE group_work(work_id BIGINT,group_id BIGINT,deleted INT)");db.execute("CREATE TABLE group_member(group_id BIGINT,user_id BIGINT,deleted INT)");db.execute("CREATE TABLE support_group(id BIGINT,deleted INT,status INT)");storage=mock(MediaStorage.class);service=new MediaService(db,storage,"test-secret-at-least-thirty-two-characters");}
 MockMultipartFile file(){byte[] b=new byte[64];b[3]=24;System.arraycopy("ftypisom".getBytes(),0,b,4,8);return new MockMultipartFile("file","sample.mp4","video/mp4",b);}
 long id(String ref){return Long.parseLong(ref.split("/")[3]);}
 @Test void rejectExtensionSpoofAndOversize(){assertThrows(BusinessException.class,()->service.upload(1L,new MockMultipartFile("file","fake.mp4","video/mp4","not a video".getBytes())));assertThrows(BusinessException.class,()->service.upload(1L,new MockMultipartFile("file","x.mov","video/quicktime",file().getBytes())));assertThrows(BusinessException.class,()->MediaService.validateMp4(new byte[8]));}
 @Test void ownersOnlyUntilPublishedAndRevocationIsImmediate()throws Exception{String ref=service.upload(1L,file());long id=id(ref);assertEquals(ref,service.requireOwned(1L,ref));assertThrows(BusinessException.class,()->service.requireOwned(2L,ref));assertThrows(BusinessException.class,()->service.playback(2L,id));assertNotNull(service.playback(3L,id));db.update("INSERT INTO work(video_url,status,visible,deleted) VALUES(?,0,1,0)",ref);assertThrows(BusinessException.class,()->service.playback(2L,id));db.update("UPDATE work SET status=1");String signed=service.playback(2L,id);var query=parse(signed);when(storage.read(anyString())).thenReturn(new ByteArrayResource(new byte[8]));assertNotNull(service.content(id,2L,Long.parseLong(query.get("expires")),query.get("signature")));db.update("UPDATE work SET status=2");assertThrows(BusinessException.class,()->service.content(id,2L,Long.parseLong(query.get("expires")),query.get("signature")));}
 @Test void privateWorkAndExpiredOrForgedSignatureDenied(){String ref=service.upload(1L,file());long id=id(ref);db.update("INSERT INTO work(video_url,status,visible,deleted) VALUES(?,1,0,0)",ref);assertThrows(BusinessException.class,()->service.playback(2L,id));var q=parse(service.playback(1L,id));assertThrows(BusinessException.class,()->service.content(id,1L,Long.parseLong(q.get("expires")),"forged"));assertThrows(BusinessException.class,()->service.content(id,1L,1,q.get("signature")));db.update("UPDATE user SET status=0 WHERE id=1");assertThrows(BusinessException.class,()->service.playback(1L,id));}
 @Test void publishedLessonAndUploadQuota(){String ref=service.upload(1L,file());db.update("INSERT INTO craft_project(id,video_url,status,deleted) VALUES(81,?,1,0)",ref);assertNotNull(service.playback(2L,id(ref)));for(int n=1;n<20;n++)service.upload(1L,file());assertThrows(BusinessException.class,()->service.upload(1L,file()));}
 Map<String,String> parse(String url){var result=new HashMap<String,String>();for(String p:url.split("\\?")[1].split("&")){var bits=p.split("=");result.put(bits[0],bits[1]);}return result;}

 // ── AU3（2026-11-04）：步骤音频上传闭环 ──

 /** ID3 头的 mp3；文件名故意伪装成 .bin 以证明走魔数嗅探而非扩展名。 */
 MockMultipartFile audio(){byte[] b=new byte[64];b[0]='I';b[1]='D';b[2]='3';return new MockMultipartFile("file","guide.bin","application/octet-stream",b);}

 @Test void audioUploadRoleGatedSniffedAndCapped(){
  // 学生（role 0）不得上传引导音频
  var denied=assertThrows(BusinessException.class,()->service.uploadAudio(1L,audio()));
  assertEquals(1003,denied.getCode());
  // 传承人（role 1）可上传；返回私有媒体引用；MIME 正确
  String ref=service.uploadAudio(4L,audio());
  assertTrue(ref.matches("/api/media/\\d+/content"),ref);
  assertEquals("audio/mpeg",service.contentType(id(ref)));
  // 伪装扩展名的假音频 → 1001
  assertThrows(BusinessException.class,()->service.uploadAudio(4L,new MockMultipartFile("file","a.mp3","audio/mpeg","noise".getBytes())));
  // 每日上限 20 次（与视频同级）
  for(int n=1;n<20;n++)service.uploadAudio(4L,audio());
  assertThrows(BusinessException.class,()->service.uploadAudio(4L,audio()));
 }

 @Test void stepAudioAudienceAuthorization()throws Exception{
  String ref=service.uploadAudio(4L,audio());long id=id(ref);
  // 未被任何已发布项目步骤引用：学生无权
  assertThrows(BusinessException.class,()->service.playback(2L,id));
  // 已发布项目步骤引用 → 学生（受众）可签名播放并取流
  db.update("INSERT INTO craft_project(id,video_url,status,deleted) VALUES(81,NULL,1,0)");
  db.update("INSERT INTO craft_step(id,project_id,audio_url,deleted) VALUES(9101,81,?,0)",ref);
  var q=parse(service.playback(2L,id));
  when(storage.read(anyString())).thenReturn(new ByteArrayResource(new byte[8]));
  assertNotNull(service.content(id,2L,Long.parseLong(q.get("expires")),q.get("signature")));
  // 项目下架 → 受众授权即时失效；所有者与管理员不受影响
  db.update("UPDATE craft_project SET status=0 WHERE id=81");
  assertThrows(BusinessException.class,()->service.playback(2L,id));
  assertNotNull(service.playback(4L,id));
  assertNotNull(service.playback(3L,id));
 }

 @Test void stepAudioRetirementSharedSafeAndIdempotent(){
  String ref=service.uploadAudio(4L,audio());long id=id(ref);
  db.update("INSERT INTO craft_project(id,video_url,status,deleted) VALUES(81,NULL,1,0)");
  db.update("INSERT INTO craft_step(id,project_id,audio_url,deleted) VALUES(9101,81,?,0),(9102,81,?,0)",ref,ref);
  // 同项目另一步骤仍引用 → 不退役
  db.update("UPDATE craft_step SET deleted=1 WHERE id=9101");
  service.retireOrphanedMedia(ref);
  assertEquals(0,db.queryForObject("SELECT deleted FROM media_asset WHERE id=?",Integer.class,id));
  // 全部解除引用 → 逻辑注销 + 登记 PRIVATE 清理；重复调用幂等
  db.update("UPDATE craft_step SET deleted=1 WHERE id=9102");
  service.retireOrphanedMedia(ref);
  assertEquals(1,db.queryForObject("SELECT deleted FROM media_asset WHERE id=?",Integer.class,id));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE'",Integer.class));
  service.retireOrphanedMedia(ref);
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM file_cleanup_job WHERE storage_kind='PRIVATE'",Integer.class));
  // 外链/静态资源/空值直接忽略
  service.retireOrphanedMedia("https://cdn.example.com/a.mp3");
  service.retireOrphanedMedia("/static/audio/101-1.mp3");
  service.retireOrphanedMedia(null);
 }

 @Test void retirementRespectsWorkAndProjectMediaRefs(){
  // AY2：引用计数覆盖作品与项目视频/封面——音频被作品引用时不得随步骤清理误删
  String ref=service.uploadAudio(4L,audio());long id=id(ref);
  db.update("INSERT INTO work(id,video_url,status,visible,deleted) VALUES(7101,?,1,1,0)",ref);
  service.retireOrphanedMedia(ref);
  assertEquals(0,db.queryForObject("SELECT deleted FROM media_asset WHERE id=?",Integer.class,id),"作品仍引用则不得退役");
  db.update("UPDATE work SET deleted=1 WHERE id=7101");
  service.retireOrphanedMedia(ref);
  assertEquals(1,db.queryForObject("SELECT deleted FROM media_asset WHERE id=?",Integer.class,id),"全引用解除后退役");
 }

 @Test void playbackIfMediaRefRewritesOrPassesThrough(){
  String ref=service.uploadAudio(4L,audio());
  // 授权不通过 → 降级 null（不抛异常拖垮列表装配）；外链/静态资源/空值原样透传
  assertNull(service.playbackIfMediaRef(2L,ref));
  assertEquals("https://cdn.example.com/a.mp3",service.playbackIfMediaRef(2L,"https://cdn.example.com/a.mp3"));
  assertEquals("/static/audio/101-1.mp3",service.playbackIfMediaRef(2L,"/static/audio/101-1.mp3"));
  assertNull(service.playbackIfMediaRef(2L,null));
  // 受众授权就绪 → 换签名播放地址
  db.update("INSERT INTO craft_project(id,video_url,status,deleted) VALUES(81,NULL,1,0)");
  db.update("INSERT INTO craft_step(id,project_id,audio_url,deleted) VALUES(9101,81,?,0)",ref);
  assertTrue(service.playbackIfMediaRef(2L,ref).contains("signature="));
 }
}
