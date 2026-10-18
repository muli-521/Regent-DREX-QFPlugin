import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.json.*;
String CFG=pluginPath+"/config";
int MAX_MUTE=2592000;
int MAX_BAN_BATCH=50;
Map groupCfgCache=new HashMap();
Map globalCfgCache=new HashMap();
Set enabledSessionCache=null;
Map nameCache=new HashMap();
Map nameCacheTs=new HashMap();
long nameTtl=60000L;
String ownerUin=null;
ArrayList materList=new ArrayList();
Map pendingCheckData=new HashMap();
Map pendingCheckTime=new HashMap();
long CHECK_PENDING_TTL=60000L;
public void dbg(String s){
try{log("main.log",s);}catch(Exception e){}
}
public String now(){
return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
}
public void sendFeedback(String groupId,String content){
try{
sendMsg(groupId,content,2);
}catch(Exception e){
dbg("sendFeedbackError: "+e);
}
}
public void sendReplyFeedback(String groupId,long replyMsgId,String content){
try{
if(replyMsgId!=0L){
sendReplyMsg(groupId,replyMsgId,content,2);
}else{
sendMsg(groupId,content,2);
}
}catch(Exception e){
dbg("sendReplyError: "+e);
try{
sendMsg(groupId,content,2);
}catch(Exception e2){
dbg("sendMsgError: "+e2);
}
}
}
public String readAll(File f){
if(f==null|| !f.exists())return"";
BufferedReader br=null;
try{
br=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));
StringBuilder sb=new StringBuilder();
String line;
while((line=br.readLine())!=null)sb.append(line).append("\n");
br.close();
br=null;
return sb.toString();
}catch(Exception e){
dbg("readAllError: "+e);
try{if(br!=null)br.close();}catch(Exception e2){}
return"";
}
}
public synchronized ArrayList readLines(File f){
ArrayList list=new ArrayList();
if(f==null|| !f.exists())return list;
String all=readAll(f);
String[]arr=all.split("\n");
for(int i=0;i<arr.length;i++){
String s=arr[i].trim();
if(s.length()>0)list.add(s);
}
return list;
}
public synchronized void appendLine(File f,String line){
PrintWriter pw=null;
try{
if(!f.getParentFile().exists())f.getParentFile().mkdirs();
pw=new PrintWriter(new OutputStreamWriter(new FileOutputStream(f,true),"UTF-8"));
pw.println(line);
pw.flush();
pw.close();
pw=null;
}catch(Exception e){
dbg("appendError: "+e);
try{if(pw!=null)pw.close();}catch(Exception e2){}
}
}
public synchronized void removeLine(File f,String target){
if(f==null|| !f.exists())return;
ArrayList lines=readLines(f);
lines.remove(target);
writeLinesAtomic(f,lines);
}
public synchronized void writeLinesAtomic(File f,ArrayList lines){
StringBuilder sb=new StringBuilder();
if(lines!=null){
for(int i=0;i<lines.size();i++){
Object o=lines.get(i);
if(o!=null)sb.append(o.toString()).append("\n");
}
}
writeTextAtomic(f,sb.toString());
}
public synchronized void writeTextAtomic(File f,String content){
PrintWriter pw=null;
try{
if(!f.getParentFile().exists())f.getParentFile().mkdirs();
File tmp=new File(f.getAbsolutePath()+".tmp");
pw=new PrintWriter(new OutputStreamWriter(new FileOutputStream(tmp),"UTF-8"));
pw.print(content);
pw.flush();
pw.close();
pw=null;
if(f.exists())f.delete();
if(!tmp.renameTo(f)){
dbg("renameFailed: "+f.getAbsolutePath());
}
}catch(Exception e){
dbg("writeTextError: "+e);
try{if(pw!=null)pw.close();}catch(Exception e2){}
}
}
public boolean containsLine(File f,String target){
return readLines(f).contains(target);
}
public String getConfigDir(){
File d=new File(CFG);
if(!d.exists())d.mkdirs();
return CFG;
}
public File getEnabledFile(){return new File(getConfigDir()+"/enabled_sessions.txt");}
public File getDelegateFile(String groupId){
File dir=new File(getConfigDir()+"/delegates");
if(!dir.exists())dir.mkdirs();
return new File(dir,groupId+".txt");
}
public File getAutoAdminFile(String groupId){
File dir=new File(getConfigDir()+"/autoadmin");
if(!dir.exists())dir.mkdirs();
return new File(dir,groupId+".txt");
}
public File getLocalAdminFile(String groupId){
File dir=new File(getConfigDir()+"/localadmin");
if(!dir.exists())dir.mkdirs();
return new File(dir,groupId+".txt");
}
public File getBanFile(String groupId){
File dir=new File(getConfigDir()+"/banlist");
if(!dir.exists())dir.mkdirs();
return new File(dir,groupId+".txt");
}
public File getWarnFile(String groupId){
File dir=new File(getConfigDir()+"/warn");
if(!dir.exists())dir.mkdirs();
return new File(dir,groupId+".txt");
}
public File getAllianceFile(){return new File(getConfigDir()+"/alliance.txt");}
public File getFbanFile(){return new File(getConfigDir()+"/fban_list.txt");}
public File getGroupConfigFile(String groupId){
File dir=new File(getConfigDir()+"/group_config");
if(!dir.exists())dir.mkdirs();
return new File(dir,groupId+".json");
}
public File getGlobalConfigFile(){return new File(getConfigDir()+"/global_config.json");}
public JSONObject loadGroupCfg(String groupId){
Object c=groupCfgCache.get(groupId);
if(c!=null)return(JSONObject)c;
JSONObject json=new JSONObject();
File f=getGroupConfigFile(groupId);
if(f.exists()){
try{
String s=readAll(f).trim();
if(s.length()>0)json=new JSONObject(s);
}catch(Exception e){
dbg("loadGroupCfg: "+e);
}
}
groupCfgCache.put(groupId,json);
return json;
}
public String getGroupConfig(String groupId,String key,String def){
try{
JSONObject json=loadGroupCfg(groupId);
String v=json.optString(key,null);
if(v==null||v.length()==0)return def;
return v;
}catch(Exception e){
return def;
}
}
public int getGroupConfigInt(String groupId,String key,int def){
try{
return Integer.parseInt(getGroupConfig(groupId,key,String.valueOf(def)));
}catch(Exception e){
return def;
}
}
public void setGroupConfig(String groupId,String key,String value){
try{
JSONObject json=loadGroupCfg(groupId);
if(value==null)json.remove(key);
else json.put(key,value);
writeTextAtomic(getGroupConfigFile(groupId),json.toString(2));
}catch(Exception e){
dbg("setGroupConfig: "+e);
}
}
public JSONObject loadGlobalCfg(){
Object c=globalCfgCache.get("g");
if(c!=null)return(JSONObject)c;
JSONObject json=new JSONObject();
File f=getGlobalConfigFile();
if(f.exists()){
try{
String s=readAll(f).trim();
if(s.length()>0)json=new JSONObject(s);
}catch(Exception e){
dbg("loadGlobalCfg: "+e);
}
}
globalCfgCache.put("g",json);
return json;
}
public String getGlobalConfig(String key,String def){
try{
String v=loadGlobalCfg().optString(key,null);
if(v==null||v.length()==0)return def;
return v;
}catch(Exception e){
return def;
}
}
public void setGlobalConfig(String key,String value){
try{
JSONObject json=loadGlobalCfg();
if(value==null)json.remove(key);
else json.put(key,value);
writeTextAtomic(getGlobalConfigFile(),json.toString(2));
}catch(Exception e){
dbg("setGlobalConfig: "+e);
}
}
public boolean isEnabled(String groupId,String feature){
return"on".equals(getGroupConfig(groupId,feature,"off"));
}
public Set getEnabledSessions(){
if(enabledSessionCache==null){
enabledSessionCache=new HashSet();
ArrayList list=readLines(getEnabledFile());
for(int i=0;i<list.size();i++)enabledSessionCache.add(list.get(i));
}
return enabledSessionCache;
}
public boolean isSessionEnabled(String groupId){
return getEnabledSessions().contains(groupId);
}
public void setSessionEnabled(String groupId,boolean on){
Set s=getEnabledSessions();
if(on){
if(!s.contains(groupId)){
s.add(groupId);
appendLine(getEnabledFile(),groupId);
}
}else{
if(s.contains(groupId)){
s.remove(groupId);
removeLine(getEnabledFile(),groupId);
}
}
}
public void addAllianceGroup(String groupId){
File f=getAllianceFile();
if(!containsLine(f,groupId))appendLine(f,groupId);
}
public void removeAllianceGroup(String groupId){removeLine(getAllianceFile(),groupId);}
public boolean isAllianceGroup(String groupId){return containsLine(getAllianceFile(),groupId);}
public ArrayList getAllianceGroups(){return readLines(getAllianceFile());}
public void addFbanUser(String uin,String op,String reason){
File f=getFbanFile();
ArrayList list=readLines(f);
ArrayList out=new ArrayList();
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
if(!firstField(line).equals(uin))out.add(line);
}
out.add(uin+";"+now()+";"+safe(op)+";"+safe(reason));
writeLinesAtomic(f,out);
}
public void removeFbanUser(String uin){
File f=getFbanFile();
ArrayList list=readLines(f);
ArrayList out=new ArrayList();
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
if(!firstField(line).equals(uin))out.add(line);
}
writeLinesAtomic(f,out);
}
public boolean isFbanUser(String uin){
ArrayList list=readLines(getFbanFile());
for(int i=0;i<list.size();i++){
if(firstField((String)list.get(i)).equals(uin))return true;
}
return false;
}
public String getFbanReason(String uin){
ArrayList list=readLines(getFbanFile());
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
if(firstField(line).equals(uin)){
String[]p=splitFields(line);
if(p.length>=4)return p[3].trim();
}
}
return"";
}
public void addBan(String groupId,String uin,String op,String reason){
File f=getBanFile(groupId);
ArrayList list=readLines(f);
ArrayList out=new ArrayList();
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
if(!firstField(line).equals(uin))out.add(line);
}
out.add(uin+";"+now()+";"+safe(op)+";"+safe(reason));
writeLinesAtomic(f,out);
}
public void removeBan(String groupId,String uin){
File f=getBanFile(groupId);
ArrayList list=readLines(f);
ArrayList out=new ArrayList();
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
if(!firstField(line).equals(uin))out.add(line);
}
writeLinesAtomic(f,out);
}
public boolean isBanned(String groupId,String uin){
ArrayList list=readLines(getBanFile(groupId));
for(int i=0;i<list.size();i++){
if(firstField((String)list.get(i)).equals(uin))return true;
}
return false;
}
public ArrayList getBanList(String groupId){return readLines(getBanFile(groupId));}
public int getWarnCount(String groupId,String uin){
ArrayList list=readLines(getWarnFile(groupId));
for(int i=0;i<list.size();i++){
String[]p=((String)list.get(i)).split("\\|");
if(p.length>=2&&p[0].equals(uin)){
try{return Integer.parseInt(p[1]);}catch(Exception e){return 0;}
}
}
return 0;
}
public int addWarn(String groupId,String uin){
File f=getWarnFile(groupId);
ArrayList list=readLines(f);
ArrayList out=new ArrayList();
int count=0;
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
String[]p=line.split("\\|");
if(p.length>=2&&p[0].equals(uin)){
try{count=Integer.parseInt(p[1]);}catch(Exception e){count=0;}
}else{
out.add(line);
}
}
count=count+1;
out.add(uin+"|"+count+"|"+now());
writeLinesAtomic(f,out);
return count;
}
public void resetWarn(String groupId,String uin){
File f=getWarnFile(groupId);
ArrayList list=readLines(f);
ArrayList out=new ArrayList();
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
String[]p=line.split("\\|");
if(p.length<1|| !p[0].equals(uin))out.add(line);
}
writeLinesAtomic(f,out);
}
public ArrayList getWarnList(String groupId){return readLines(getWarnFile(groupId));}
public String firstField(String line){
if(line==null)return"";
int i=line.indexOf(";");
int j=line.indexOf("|");
if(i<0){
i=j;
}else if(j>=0&&j<i){
i=j;
}
if(i<0)return line.trim();
return line.substring(0,i).trim();
}
public String safe(String s){
if(s==null)return"";
return s.replace("|","/").replace(";","/").replace("\n"," ").replace("\r"," ");
}
public String[]splitFields(String line){
if(line==null)return new String[0];
if(line.indexOf("|")>=0)return line.split("\\|",4);
if(line.indexOf(";")>=0)return line.split(";",4);
return new String[]{line.trim()};
}
public String banTimeOf(String gid,String uin){
ArrayList list=readLines(getBanFile(gid));
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
if(!firstField(line).equals(uin))continue;
String[]p=splitFields(line);
if(p.length>=2)return p[1].trim();
return"";
}
return"";
}
public void loadOwnerUin(){
try{
File f=new File(getConfigDir()+"/owner.txt");
if(!f.exists())return;
java.util.Scanner sc=new java.util.Scanner(f,"UTF-8");
if(sc.hasNextLine()){
String s=sc.nextLine().trim();
if(s.matches("\\d{5,12}"))ownerUin=s;
}
sc.close();
}catch(Exception e){
dbg("loadOwner: "+e);
}
}
public void loadMaterList(){
try{
File f=new File(getConfigDir()+"/master.txt");
if(!f.exists()){
File legacy=new File(getConfigDir()+"/mater.txt");
if(legacy.exists()){
ArrayList old=readLines(legacy);
writeLinesAtomic(f,old);
f=legacy;
dbg("master: 已从 mater.txt 迁移");
}
}
if(!f.exists())return;
ArrayList lines=readLines(f);
for(int i=0;i<lines.size();i++){
String s=((String)lines.get(i)).trim();
if(s.matches("\\d{5,12}")&& !materList.contains(s))materList.add(s);
}
}catch(Exception e){
dbg("loadMater: "+e);
}
}
public void syncGroupAdmins(String groupId){
try{
ArrayList members=getGroupMemberList(groupId);
if(members==null||members.isEmpty())return;
ArrayList autos=new ArrayList();
for(int i=0;i<members.size();i++){
Object m=members.get(i);
if(m==null)continue;
String uin=null;
try{
Object uinObj=optField(m,"uin");
if(uinObj!=null)uin=uinObj.toString();
}catch(Exception e){continue;}
if(uin==null||uin.length()==0)continue;
if(myUin!=null&&uin.equals(myUin))continue;
Object roleObj=optField(m,"role");
if(roleObj==null)roleObj=optField(m,"memberRole");
if(roleObj==null)roleObj=optField(m,"administrator");
if(roleObj==null)continue;
String role=roleObj.toString().toLowerCase();
boolean isAdmin=role.contains("owner")||role.contains("admin")
||role.equals("1")||role.equals("2")||role.equals("true");
if(!isAdmin)continue;
if(!autos.contains(uin))autos.add(uin);
}
if(!autos.isEmpty())writeLinesAtomic(getAutoAdminFile(groupId),autos);
}catch(Exception e){
dbg("syncAdmin: "+e);
}
}
public boolean isOwner(String uin){
if(uin==null)return false;
if(ownerUin!=null&&ownerUin.length()>0&&uin.equals(ownerUin))return true;
if(materList!=null&&materList.contains(uin))return true;
return myUin!=null&&uin.equals(myUin);
}
public boolean isListMaster(String uin){
if(uin==null)return false;
if(ownerUin!=null&&ownerUin.length()>0)return uin.equals(ownerUin);
return myUin!=null&&uin.equals(myUin);
}
public boolean isGroupOwner(String groupId,String uin){
if(uin==null)return false;
try{
ArrayList members=getGroupMemberList(groupId);
if(members==null)return false;
for(int i=0;i<members.size();i++){
Object m=members.get(i);
if(m==null)continue;
String mu=null;
try{
Object uo=optField(m,"uin");
if(uo!=null)mu=uo.toString();
}catch(Exception e){continue;}
if(mu==null|| !mu.equals(uin))continue;
Object ro=optField(m,"role");
if(ro==null)ro=optField(m,"memberRole");
if(ro==null)ro=optField(m,"administrator");
if(ro==null)return false;
return ro.toString().toLowerCase().contains("owner");
}
}catch(Exception e){
dbg("isGroupOwner: "+e);
}
return false;
}
public boolean isDelegate(String groupId,String uin){
if(uin==null)return false;
if(isOwner(uin))return true;
if(containsLine(getDelegateFile(groupId),uin))return true;
if(containsLine(getAutoAdminFile(groupId),uin))return true;
return containsLine(getLocalAdminFile(groupId),uin);
}
public boolean canManageLocal(String groupId,String uin){
if(uin==null)return false;
if(isOwner(uin))return true;
return containsLine(getAutoAdminFile(groupId),uin);
}
public ArrayList getAllDelegates(String groupId){
ArrayList all=readLines(getDelegateFile(groupId));
ArrayList local=readLines(getLocalAdminFile(groupId));
for(int i=0;i<local.size();i++){
String u=(String)local.get(i);
if(!all.contains(u))all.add(u);
}
ArrayList auto=readLines(getAutoAdminFile(groupId));
for(int i=0;i<auto.size();i++){
String u=(String)auto.get(i);
if(!all.contains(u))all.add(u);
}
return all;
}
public String renderAdminList(String targetGroup,String currentGroupId){
ArrayList all=new ArrayList();
ArrayList tags=new ArrayList();
ArrayList manualL=readLines(getDelegateFile(targetGroup));
for(int i=0;i<manualL.size();i++){
String u=(String)manualL.get(i);
if(!all.contains(u)){all.add(u);tags.add("");}
}
ArrayList localL=readLines(getLocalAdminFile(targetGroup));
for(int i=0;i<localL.size();i++){
String u=(String)localL.get(i);
if(all.contains(u))continue;
all.add(u);tags.add(" [本地]");
}
ArrayList autoL=readLines(getAutoAdminFile(targetGroup));
for(int i=0;i<autoL.size();i++){
String u=(String)autoL.get(i);
if(all.contains(u))continue;
all.add(u);tags.add(" [自动]");
}
String label=targetGroup.equals(currentGroupId)?"本群":("群 "+targetGroup);
if(all.isEmpty())return label+"暂无代管";
StringBuilder sb=new StringBuilder();
sb.append(label).append("代管(").append(all.size()).append("人):\n");
for(int i=0;i<all.size();i++){
String u=(String)all.get(i);
sb.append(i+1).append(". ").append(nm(targetGroup,u)).append("(").append(u).append(")");
sb.append((String)tags.get(i)).append("\n");
}
sb.append("标 [本地] 的由群内管理员添加, [自动] 的为系统识别的群主/管理员, 无标签的由宿主添加");
return sb.toString();
}
public boolean isOwnerOrDelegate(String groupId,String uin){
return isOwner(uin)||isDelegate(groupId,uin);
}
public boolean isProtected(String groupId,String target){
if(target==null)return false;
if(isOwner(target))return true;
return isDelegate(groupId,target);
}
public boolean canOperate(String groupId,String operator,String target){
if(operator==null||target==null)return false;
if(isOwner(operator))return true;
if(isDelegate(groupId,operator)&& !isProtected(groupId,target))return true;
return false;
}
public boolean checkTarget(String groupId,String op,String target,long replyMsgId,String act){
if(isProtected(groupId,target)){
if(isOwner(target)){
sendReplyFeedback(groupId,replyMsgId,act+"失败: 不能操作宿主");
}else{
sendReplyFeedback(groupId,replyMsgId,act+"失败: "+nm(groupId,target)+" 是代管，受保护");
}
return false;
}
if(!canOperate(groupId,op,target)){
sendReplyFeedback(groupId,replyMsgId,act+"失败: 权限不足");
return false;
}
return true;
}
public String nm(String groupId,String uin){
if(uin==null||uin.length()==0)return"未知";
String key=groupId+"|"+uin;
long nowMs=System.currentTimeMillis();
Object tsObj=nameCacheTs.get(key);
if(tsObj!=null&&(nowMs-toLong(tsObj))<nameTtl){
Object cached=nameCache.get(key);
if(cached!=null)return cached.toString();
}
String name=queryName(groupId,uin);
if(nameCache.size()>500){
nameCache.clear();
nameCacheTs.clear();
}
nameCache.put(key,name);
nameCacheTs.put(key,String.valueOf(nowMs));
return name;
}
public String queryName(String groupId,String uin){
try{
if(groupId!=null&&groupId.length()>0){
Object mem=getMemberInfo(groupId,uin);
if(mem!=null){
try{
String n=(String)mem.uinName;
if(n!=null&&n.length()>0)return n;
}catch(Exception e1){}
}
}
ArrayList friends=getAllFriend();
if(friends!=null){
for(int i=0;i<friends.size();i++){
Object f=friends.get(i);
String fuin=null;
try{fuin=(String)f.uin;}catch(Exception e2){}
if(fuin==null){
try{fuin=String.valueOf(f.getClass().getField("uin").get(f));}catch(Exception e3){}
}
if(fuin!=null&&fuin.equals(uin)){
String remark=null;
try{remark=(String)f.remark;}catch(Exception e4){}
if(remark!=null&&remark.length()>0)return remark;
String name=null;
try{name=(String)f.name;}catch(Exception e5){}
if(name!=null&&name.length()>0)return name;
}
}
}
}catch(Exception e){
dbg("nmError: "+e);
}
return uin;
}
public String roleZh(String role){
if("OWNER".equals(role))return"群主";
if("ADMIN".equals(role))return"管理员";
if("MEMBER".equals(role))return"成员";
return role==null?"未知":role;
}
public String formatTs(long sec){
if(sec<=0)return"未知";
try{
return new SimpleDateFormat("yyyy-MM-dd").format(new Date(sec*1000L));
}catch(Exception e){
return String.valueOf(sec);
}
}
public long toLong(Object o){
if(o==null)return 0L;
try{
return Long.parseLong(o.toString().trim());
}catch(Exception e){
return 0L;
}
}
public Object optField(Object o,String name){
if(o==null)return null;
try{
java.lang.reflect.Field f=o.getClass().getField(name);
return f.get(o);
}catch(Exception e){
return null;
}
}
Set targetCommands=null;
public boolean needsTarget(String cmd){
if(targetCommands==null){
targetCommands=new HashSet();
targetCommands.add("/mute");
targetCommands.add("/unmute");
targetCommands.add("/kick");
targetCommands.add("/ban");
targetCommands.add("/unban");
targetCommands.add("/warn");
targetCommands.add("/fban");
targetCommands.add("/unfban");
targetCommands.add("/admin");
}
return targetCommands.contains(cmd);
}
public Object findFieldLike(Object o,String keyword){
if(o==null)return null;
java.lang.reflect.Field[]fs=null;
try{fs=o.getClass().getFields();}catch(Exception e){return null;}
if(fs==null)return null;
for(int i=0;i<fs.length;i++){
String n=fs[i].getName().toLowerCase();
if(n.indexOf(keyword)<0)continue;
try{
Object v=fs[i].get(o);
if(v!=null)return v;
}catch(Exception e){}
}
return null;
}
public String tryUid(String uid){
if(uid==null)return null;
String s=uid.trim();
if(s.length()==0)return null;
if(s.matches("\\d{5,12}"))return s;
try{
String u=getUinFromUid(s);
if(u!=null&&u.matches("\\d{5,12}"))return u;
}catch(Exception e){}
return null;
}
public String pickUin(Object o){
if(o==null)return null;
java.lang.reflect.Field[]fs=null;
try{fs=o.getClass().getFields();}catch(Exception e){return null;}
if(fs==null)return null;
ArrayList cands=new ArrayList();
ArrayList names=new ArrayList();
ArrayList tailCands=new ArrayList();
ArrayList tailNames=new ArrayList();
for(int i=0;i<fs.length;i++){
String n=fs[i].getName().toLowerCase();
if(n.startsWith("_bsh"))continue;
if(n.indexOf("uid")<0&&n.indexOf("uin")<0)continue;
Object v=null;
try{v=fs[i].get(o);}catch(Exception e){continue;}
if(v==null)continue;
String s=v.toString().trim();
if(s.length()==0||"0".equals(s)||"-1".equals(s)||"null".equals(s))continue;
if(s.matches("\\d{5,12}")&&n.indexOf("uin")>=0){
dbg("pickUin: "+fs[i].getName()+"="+s+" (uin 直用)");
return s;
}
if(n.indexOf("sender")>=0||n.indexOf("from")>=0){
cands.add(s);
names.add(fs[i].getName());
}else{
tailCands.add(s);
tailNames.add(fs[i].getName());
}
}
for(int i=0;i<tailCands.size();i++){
cands.add(tailCands.get(i));
names.add(tailNames.get(i));
}
for(int i=0;i<cands.size();i++){
String u=tryUid((String)cands.get(i));
if(u!=null){
dbg("pickUin: "+names.get(i)+"="+cands.get(i)+" -> "+u);
return u;
}
}
return null;
}
public String getQuotedSenderUin(Object msg){
try{
Object rec=optField(msg,"data");
if(rec==null)return null;
Object elsObj=optField(rec,"elements");
if(!(elsObj instanceof List))return null;
List els=(List)elsObj;
for(int i=0;i<els.size();i++){
Object el=els.get(i);
if(el==null)continue;
Object reply=findFieldLike(el,"repl");
if(reply==null)continue;
String uin=pickUin(reply);
if(uin!=null&&uin.length()>0)return uin;
}
}catch(Exception e){
dbg("quotedError: "+e);
}
return null;
}
public Object getReplyElement(Object msg){
try{
Object rec=optField(msg,"data");
if(rec==null)return null;
Object elsObj=optField(rec,"elements");
if(!(elsObj instanceof List))return null;
List els=(List)elsObj;
for(int i=0;i<els.size();i++){
Object el=els.get(i);
if(el==null)continue;
Object reply=findFieldLike(el,"repl");
if(reply!=null)return reply;
}
}catch(Exception e){
dbg("replyElementError: "+e);
}
return null;
}
public String dumpFields(Object o){
if(o==null)return"null";
StringBuilder sb=new StringBuilder();
try{
java.lang.reflect.Field[]fs=o.getClass().getFields();
for(int i=0;i<fs.length;i++){
if(fs[i].getName().startsWith("_bsh"))continue;
if(sb.length()>0)sb.append(", ");
sb.append(fs[i].getName()).append(":").append(fs[i].getType().getSimpleName());
}
}catch(Exception e){}
return sb.toString();
}
public String dumpUidValues(Object o){
if(o==null)return"null";
StringBuilder sb=new StringBuilder();
try{
java.lang.reflect.Field[]fs=o.getClass().getFields();
for(int i=0;i<fs.length;i++){
String n=fs[i].getName().toLowerCase();
if(n.startsWith("_bsh"))continue;
if(n.indexOf("uid")<0&&n.indexOf("uin")<0)continue;
Object v=null;
try{v=fs[i].get(o);}catch(Exception e){continue;}
if(sb.length()>0)sb.append(", ");
sb.append(fs[i].getName()).append("=").append(v);
}
}catch(Exception e){}
return sb.length()==0?"(无 uid/uin 字段)":sb.toString();
}
public ArrayList getQuotedQqs(Object msg,String excludeUin){
ArrayList out=new ArrayList();
Object reply=getReplyElement(msg);
if(reply==null)return out;
java.lang.reflect.Field[]fs=null;
try{fs=reply.getClass().getFields();}catch(Exception e){return out;}
if(fs==null)return out;
Set exclude=new HashSet();
if(excludeUin!=null&&excludeUin.length()>0)exclude.add(excludeUin);
StringBuilder all=new StringBuilder();
for(int i=0;i<fs.length;i++){
String n=fs[i].getName().toLowerCase();
if(n.startsWith("_bsh"))continue;
Object v=null;
try{v=fs[i].get(reply);}catch(Exception e){continue;}
if(v==null)continue;
if(n.indexOf("time")>=0||n.indexOf("seq")>=0||n.indexOf("random")>=0||n.indexOf("msgid")>=0){
String s=v.toString().trim();
if(s.matches("\\d{5,12}"))exclude.add(s);
}
if(v instanceof CharSequence)all.append(v.toString()).append("\n");
}
String text=all.toString();
if(text.length()==0)return out;
String[]markPats=new String[]{
"[#＃]\\s*(\\d{5,12})\\s*[#＃]",
"【\\s*(\\d{5,12})\\s*】",
"「\\s*(\\d{5,12})\\s*」",
"\\*\\s*(\\d{5,12})\\s*\\*",
"<\\s*(\\d{5,12})\\s*>",
"\\$\\s*(\\d{5,12})\\s*\\$",
"〖\\s*(\\d{5,12})\\s*〗"
};
for(int pi=0;pi<markPats.length;pi++){
java.util.regex.Matcher mm=java.util.regex.Pattern
.compile(markPats[pi]).matcher(text);
while(mm.find()){
String q=mm.group(1);
if(exclude.contains(q))continue;
if(!out.contains(q))out.add(q);
if(out.size()>=MAX_BAN_BATCH)return out;
}
}
if(!out.isEmpty())return out;
java.util.regex.Matcher m=java.util.regex.Pattern
.compile("(?<!\\d)\\d{5,12}(?!\\d)").matcher(text);
while(m.find()){
String q=m.group();
if(exclude.contains(q))continue;
if(!out.contains(q))out.add(q);
if(out.size()>=MAX_BAN_BATCH)return out;
}
return out;
}
public int parseTimeToSeconds(String s){
if(s==null)return-1;
s=s.toLowerCase().trim();
if(s.length()==0)return-1;
long mult;
String numPart;
if(s.endsWith("mth")){
mult=2592000L;
numPart=s.substring(0,s.length()-3);
if(numPart.length()==0)numPart="1";
}else{
char unit=s.charAt(s.length()-1);
if(unit=='s')mult=1L;
else if(unit=='m')mult=60L;
else if(unit=='h')mult=3600L;
else if(unit=='d')mult=86400L;
else if(unit>='0'&&unit<='9')mult=1L;
else return-1;
numPart=(unit>='0'&&unit<='9')?s:s.substring(0,s.length()-1);
}
long num;
try{
num=Long.parseLong(numPart);
}catch(Exception e){
return-1;
}
long r=num*mult;
if(r<=0L||r>(long)MAX_MUTE)return-1;
return(int)r;
}
public String formatTime(int seconds){
if(seconds<=0)return"0秒";
if(seconds<60)return seconds+"秒";
if(seconds<3600){
int m=seconds/60;
int s=seconds%60;
return m+"分"+(s>0?s+"秒":"");
}
if(seconds<86400){
int h=seconds/3600;
int m=(seconds%3600)/60;
return h+"小时"+(m>0?m+"分":"");
}
int d=seconds/86400;
int h=(seconds%86400)/3600;
return d+"天"+(h>0?h+"小时":"");
}
class CommandArgs{
String command;
String subCommand;
ArrayList targets;
String timeStr;
int timeSeconds;
String reason;
int atCount;
boolean targetFromQuote;
String quotedUin;
ArrayList quotedQqs;
boolean hasReply;
boolean hasError;
String errorMsg;
}
public boolean isAtToken(String p){
if(p==null)return false;
if(p.startsWith("@"))return true;
if(p.startsWith("[atUin="))return true;
if(p.startsWith("[at="))return true;
return false;
}
public CommandArgs parseCommand(String text,ArrayList atList){
CommandArgs args=new CommandArgs();
args.targets=new ArrayList();
args.timeSeconds= -1;
if(text==null|| !text.startsWith("/")){
args.hasError=true;
args.errorMsg="not_a_command";
return args;
}
String[]parts=text.split("\\s+");
if(parts.length==0||parts[0].length()==0){
args.hasError=true;
args.errorMsg="empty_command";
return args;
}
args.command=parts[0].toLowerCase();
Set subCommands=new HashSet();
subCommands.add("list");
subCommands.add("all");
subCommands.add("add");
subCommands.add("rm");
subCommands.add("clear");
subCommands.add("reset");
subCommands.add("cancel");
subCommands.add("status");
subCommands.add("check");
subCommands.add("setuser");
subCommands.add("setgroup");
subCommands.add("kick");
subCommands.add("ban");
subCommands.add("fban");
subCommands.add("mute");
int idx=1;
if(parts.length>idx&&subCommands.contains(parts[idx].toLowerCase())){
args.subCommand=parts[idx].toLowerCase();
idx=idx+1;
}
if(atList!=null){
for(int i=0;i<atList.size();i++){
Object uin=atList.get(i);
if(uin==null)continue;
String s=uin.toString().trim();
if(s.length()==0)continue;
if(s.equals(myUin))continue;
if(!args.targets.contains(s))args.targets.add(s);
}
}
args.atCount=args.targets.size();
while(parts.length>idx&&isAtToken(parts[idx]))idx=idx+1;
while(parts.length>idx&&parts[idx].matches("\\d{5,12}")){
if(!args.targets.contains(parts[idx]))args.targets.add(parts[idx]);
idx=idx+1;
}
if(parts.length>idx&&parts[idx].matches("\\d+[smMhHdD]|\\d*[mM][tT][hH]|\\d+")){
args.timeStr=parts[idx].toLowerCase();
args.timeSeconds=parseTimeToSeconds(args.timeStr);
idx=idx+1;
}
if(parts.length>idx){
StringBuilder sb=new StringBuilder();
for(int i=idx;i<parts.length;i++){
if(sb.length()>0)sb.append(" ");
sb.append(parts[i]);
}
String r=sb.toString().trim();
if(r.length()>0)args.reason=r;
}
return args;
}
public File getLogFile(String groupId){
File dir=new File(getConfigDir()+"/log");
if(!dir.exists())dir.mkdirs();
return new File(dir,groupId+".log");
}
public String nameOf(String groupId,String uin){
if(uin==null||uin.length()==0)return"";
if(!uin.matches("\\d{5,12}"))return uin;
return nm(groupId,uin);
}
public String actionZh(String a){
if(a==null)return"";
if(a.startsWith("/mute all"))return"全体禁言";
if(a.startsWith("/mute"))return"禁言";
if(a.startsWith("/unmute all"))return"全体解禁";
if(a.startsWith("/unmute"))return"解禁";
if(a.startsWith("/kick"))return"踢出";
if(a.startsWith("/ban kick"))return"一键踢出";
if(a.startsWith("/ban add")||a.startsWith("/ban setuser")||a.startsWith("/ban setgroup"))return"拉黑";
if(a.startsWith("/ban"))return"踢黑";
if(a.startsWith("/unban"))return"解除黑名单";
if(a.startsWith("/warn auto-kick"))return"警告达上限踢出";
if(a.startsWith("/warn reset"))return"清空警告";
if(a.startsWith("/warn"))return"警告";
if(a.startsWith("/fban"))return"联盟封禁";
if(a.startsWith("/unfban"))return"解除联盟封禁";
if(a.startsWith("/admin"))return"代管变更";
if(a.startsWith("/alliance"))return"联盟变更";
if(a.startsWith("/toggle"))return"开关变更";
if(a.startsWith("/set"))return"配置变更";
if(a.startsWith("/on"))return"启用群管";
if(a.startsWith("/off"))return"禁用群管";
if(a.startsWith("/autoban"))return"退群自动拉黑";
if(a.startsWith("/auto-unmute"))return"自动解禁代管";
if(a.startsWith("/external-mute"))return"外部禁言";
if(a.startsWith("/external-unmute"))return"外部解禁";
if(a.startsWith("/join"))return"入群";
if(a.startsWith("/quit"))return"退群";
if(a.startsWith("/block"))return"黑名单拦截";
return a;
}
public void writeLog(String groupId,String op,String action,String target,String detail){
try{
File f=getLogFile(groupId);
StringBuilder sb=new StringBuilder();
sb.append("[").append(now()).append("] ");
sb.append(actionZh(action));
String opName=nameOf(groupId,op);
sb.append(" | 操作:").append(op);
if(!opName.equals(op))sb.append("(").append(opName).append(")");
if(target!=null&&target.length()>0){
String tName=nameOf(groupId,target);
sb.append(" 目标:").append(target);
if(!tName.equals(target))sb.append("(").append(tName).append(")");
}
if(detail!=null&&detail.length()>0)sb.append(" | ").append(safe(detail));
appendLine(f,sb.toString());
trimLog(f);
}catch(Exception e){
dbg("writeLog: "+e);
}
}
public void trimLog(File f){
ArrayList lines=readLines(f);
if(lines.size()<=2000)return;
ArrayList out=new ArrayList();
for(int i=lines.size()-1000;i<lines.size();i++)out.add(lines.get(i));
writeLinesAtomic(f,out);
}
public boolean doMute(String groupId,String op,String target,int seconds,String reason,long replyMsgId){
try{
shutUp(groupId,target,(long)seconds);
StringBuilder fb=new StringBuilder();
fb.append("禁言成功! ").append(nm(groupId,target)).append("(").append(target).append(")");
fb.append(" 时长: ").append(formatTime(seconds));
if(reason!=null&&reason.length()>0)fb.append(" 理由: ").append(reason);
fb.append(" 执行人: [atUin=").append(op).append("]");
sendReplyFeedback(groupId,replyMsgId,fb.toString());
writeLog(groupId,op,"/mute",target,formatTime(seconds)+" "+(reason==null?"":reason));
return true;
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"禁言失败: "+e.getMessage());
dbg("doMute: "+e);
return false;
}
}
public boolean doUnmute(String groupId,String op,String target,long replyMsgId){
try{
shutUp(groupId,target,0L);
sendReplyFeedback(groupId,replyMsgId,"已解禁 "+nm(groupId,target)+"("+target+") 执行人: [atUin="+op+"]");
writeLog(groupId,op,"/unmute",target,"");
return true;
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"解禁失败: "+e.getMessage());
return false;
}
}
public boolean doKick(String groupId,String op,String target,String reason,long replyMsgId){
try{
kickGroup(groupId,target,false);
StringBuilder fb=new StringBuilder();
fb.append("已踢出 ").append(nm(groupId,target)).append("(").append(target).append(")");
if(reason!=null&&reason.length()>0)fb.append(" 理由: ").append(reason);
fb.append(" 执行人: [atUin=").append(op).append("]");
sendReplyFeedback(groupId,replyMsgId,fb.toString());
writeLog(groupId,op,"/kick",target,reason);
return true;
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"踢出失败: "+e.getMessage());
return false;
}
}
public boolean doBan(String groupId,String op,String target,String reason,long replyMsgId){
try{
kickGroup(groupId,target,true);
addBan(groupId,target,op,reason);
StringBuilder fb=new StringBuilder();
fb.append("踢黑成功! ").append(nm(groupId,target)).append("(").append(target).append(")");
if(reason!=null&&reason.length()>0)fb.append(" 理由: ").append(reason);
fb.append(" 执行人: [atUin=").append(op).append("]");
sendReplyFeedback(groupId,replyMsgId,fb.toString());
writeLog(groupId,op,"/ban",target,reason);
return true;
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"踢黑失败: "+e.getMessage());
return false;
}
}
public void doFban(String groupId,String target,String reason,String op){
kickGroup(groupId,target,true);
addFbanUser(target,op,reason);
final String ft=target;
final String fg=groupId;
new Thread(new Runnable(){
public void run(){
ArrayList groups=getAllianceGroups();
for(int i=0;i<groups.size();i++){
String gid=(String)groups.get(i);
if(gid!=null&& !gid.equals(fg)){
try{
kickGroup(gid,ft,true);
Thread.sleep(200L);
}catch(Exception e){
dbg("fbanBroadcast: "+e);
}
}
}
}
}).start();
writeLog(groupId,op,"/fban",target,reason);
}
public void showMuteList(String groupId,long replyMsgId){
try{
ArrayList list=getProhibitList(groupId);
if(list==null||list.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"当前没有人被禁言");
return;
}
StringBuilder sb=new StringBuilder("禁言列表("+list.size()+"人):\n");
for(int i=0;i<list.size();i++){
try{
Object item=list.get(i);
String uin=item.user==null?null:item.user.toString();
String name=item.userName==null?null:item.userName.toString();
long left=toLong(item.time);
if(uin!=null){
sb.append(i+1).append(". ");
sb.append(name!=null?name:uin).append("(").append(uin).append(")");
sb.append(" 剩余: ").append(formatTime((int)left)).append("\n");
}
}catch(Exception e){
dbg("muteList item: "+e);
}
}
sendReplyFeedback(groupId,replyMsgId,sb.toString().trim());
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"获取禁言列表失败: "+e.getMessage());
}
}
public void showBanList(String groupId,long replyMsgId,String targetGroup){
ArrayList list=readLines(getBanFile(targetGroup));
String label=targetGroup.equals(groupId)?"本群":("群 "+targetGroup+" 的");
if(list.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,label+"黑名单为空");
return;
}
StringBuilder sb=new StringBuilder(label+"黑名单("+list.size()+"人):\n");
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
String[]p=splitFields(line);
String uin=p.length>0?p[0]:line;
sb.append(i+1).append(". ").append(nm(targetGroup,uin)).append("(").append(uin).append(")");
sb.append(" 时间: ").append(p.length>=2&&p[1].length()>0?p[1]:"未知");
if(p.length>=3&&p[2].length()>0)sb.append(" by ").append(p[2]);
sb.append(" 理由: ").append(p.length>=4&&p[3].length()>0?p[3]:"无");
sb.append("\n");
}
sb.append("用 /unban @用户 解除; /ban check ").append(targetGroup).append(" 可核对本群成员");
String out=sb.toString().trim();
if(out.length()>3000)out=out.substring(0,3000)+"...";
sendReplyFeedback(groupId,replyMsgId,out);
}
public Set getBanUins(String gid){
Set s=new HashSet();
ArrayList list=readLines(getBanFile(gid));
for(int i=0;i<list.size();i++)s.add(firstField((String)list.get(i)));
return s;
}
public String banReasonOf(String gid,String uin){
ArrayList list=readLines(getBanFile(gid));
for(int i=0;i<list.size();i++){
String line=(String)list.get(i);
if(!firstField(line).equals(uin))continue;
String[]p=splitFields(line);
if(p.length>=4)return p[3].trim();
return"";
}
return"";
}
public void checkQuotedQqsAgainstBan(String groupId,long replyMsgId,String targetGroup,ArrayList qqs){
String label=targetGroup.equals(groupId)?"本群":("群 "+targetGroup+" 的");
Set banned=getBanUins(targetGroup);
if(banned.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,label+"黑名单为空");
return;
}
ArrayList hit=new ArrayList();
ArrayList miss=new ArrayList();
for(int i=0;i<qqs.size();i++){
String u=(String)qqs.get(i);
if(banned.contains(u))hit.add(u);
else miss.add(u);
}
StringBuilder sb=new StringBuilder();
if(!hit.isEmpty()){
sb.append("命中").append(label).append("黑名单 ").append(hit.size()).append(" 人:\n");
for(int i=0;i<hit.size();i++){
String u=(String)hit.get(i);
String r=banReasonOf(targetGroup,u);
sb.append(i+1).append(". ").append(u);
if(r.length()>0)sb.append(" 理由: ").append(r);
sb.append("\n");
}
}
if(!miss.isEmpty()){
if(sb.length()>0)sb.append("\n");
sb.append("未命中 ").append(miss.size()).append(" 人:\n");
for(int i=0;i<miss.size();i++){
sb.append(i+1).append(". ").append((String)miss.get(i)).append("\n");
}
}
if(!hit.isEmpty()|| !miss.isEmpty()){
java.util.Map data=new java.util.HashMap();
data.put("hits",hit);
data.put("misses",miss);
data.put("target",targetGroup);
pendingCheckData.put(groupId,data);
pendingCheckTime.put(groupId,Long.valueOf(System.currentTimeMillis()));
if(!hit.isEmpty())sb.append("回复 yes 一键踢出 (60 秒内有效)\n");
if(!miss.isEmpty())sb.append("回复 add 将未命中者加入黑名单 (60 秒内有效)");
}
String out=sb.toString().trim();
if(out.length()>3000)out=out.substring(0,3000)+"...";
sendReplyFeedback(groupId,replyMsgId,out);
}
public void checkBannedMembers(String groupId,String op,long replyMsgId,String targetGroup,boolean doKick){
String label=targetGroup.equals(groupId)?"本群":("群 "+targetGroup);
Set banned=getBanUins(targetGroup);
if(banned.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,label+"黑名单为空");
return;
}
ArrayList members=null;
try{members=getGroupMemberList(targetGroup);}catch(Exception e){dbg("memberList: "+e);}
if(members==null||members.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"获取"+label+"成员列表失败");
return;
}
ArrayList hits=new ArrayList();
StringBuilder detail=new StringBuilder();
for(int i=0;i<members.size();i++){
try{
Object m=members.get(i);
String uin=m.uin==null?"":m.uin.toString();
if(uin.length()==0||isOwner(uin))continue;
if(!banned.contains(uin))continue;
hits.add(uin);
String name=m.uinName==null?uin:m.uinName.toString();
String r=banReasonOf(targetGroup,uin);
detail.append(hits.size()).append(". ").append(name).append("(").append(uin).append(")");
if(r.length()>0)detail.append(" 理由: ").append(r);
detail.append("\n");
}catch(Exception e){}
}
if(hits.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,label+"成员中没有命中黑名单的人");
return;
}
if(!doKick){
java.util.Map data=new java.util.HashMap();
data.put("hits",hits);
data.put("misses",new ArrayList());
data.put("target",targetGroup);
pendingCheckData.put(groupId,data);
pendingCheckTime.put(groupId,Long.valueOf(System.currentTimeMillis()));
StringBuilder sb=new StringBuilder();
sb.append(label).append("成员命中黑名单 ").append(hits.size()).append(" 人:\n");
sb.append(detail.toString());
sb.append("回复 yes 一键踢出 (60 秒内有效)");
String out=sb.toString().trim();
if(out.length()>3000)out=out.substring(0,3000)+"...";
sendReplyFeedback(groupId,replyMsgId,out);
return;
}
int ok=0;
for(int i=0;i<hits.size();i++){
String uin=(String)hits.get(i);
try{
kickGroup(targetGroup,uin,true);
ok=ok+1;
writeLog(targetGroup,op,"/ban kick",uin,"命中"+label+"黑名单");
}catch(Exception e){
dbg("banKick: "+e);
}
}
StringBuilder sb=new StringBuilder();
sb.append("已一键踢出 ").append(ok).append(" 人\n").append(detail.toString());
String out=sb.toString().trim();
if(out.length()>3000)out=out.substring(0,3000)+"...";
sendReplyFeedback(groupId,replyMsgId,out);
}
public void handleSet(String groupId,String op,String text,long replyMsgId){
if(!isOwnerOrDelegate(groupId,op)){
sendReplyFeedback(groupId,replyMsgId,"权限不足");
return;
}
String[]parts=text.split("\\s+",3);
if(parts.length<3){
sendReplyFeedback(groupId,replyMsgId,
"格式错误: /set <键> <值>\n"+
"可用键: warnlimit(次) / joincheck(kick|ask|off) / notifygroup(群号)\n"+
"示例: /set warnlimit 3\n/set joincheck ask\n/set notifygroup 123456789");
return;
}
String key=parts[1].toLowerCase();
String value=parts[2].trim();
if("warnlimit".equals(key)){
int n;
try{n=Integer.parseInt(value);}catch(Exception e){n= -1;}
if(n<1||n>20){
sendReplyFeedback(groupId,replyMsgId,"警告阈值需在 1~20 之间");
return;
}
setGroupConfig(groupId,"warnlimit",String.valueOf(n));
sendReplyFeedback(groupId,replyMsgId,"警告阈值已设为 "+n+" 次 执行人: [atUin="+op+"]");
writeLog(groupId,op,"/set warnlimit","",String.valueOf(n));
}else if("joincheck".equals(key)){
String v=value.toLowerCase();
if(!"kick".equals(v)&& !"ask".equals(v)&& !"off".equals(v)){
sendReplyFeedback(groupId,replyMsgId,
"joincheck 可选值:\nkick = 命中黑名单直接踢出\nask = 询问管理员(默认)\noff = 不处理");
return;
}
setGroupConfig(groupId,"joincheck",v);
sendReplyFeedback(groupId,replyMsgId,
"入群黑名单处理方式已设为: "+joinCheckZh(v)+" 执行人: [atUin="+op+"]");
writeLog(groupId,op,"/set joincheck","",v);
}else if("notifygroup".equals(key)){
if("off".equals(value)||"none".equals(value)){
setGroupConfig(groupId,"notifygroup",null);
sendReplyFeedback(groupId,replyMsgId,"通知群已关闭");
return;
}
if(!value.matches("\\d{5,12}")){
sendReplyFeedback(groupId,replyMsgId,"群号格式错误: "+value);
return;
}
setGroupConfig(groupId,"notifygroup",value);
sendReplyFeedback(groupId,replyMsgId,
"通知群已设为 "+value+"\n入群命中黑名单时, 提醒只发到该群(本群不再提醒) 执行人: [atUin="+op+"]");
writeLog(groupId,op,"/set notifygroup","",value);
}else{
sendReplyFeedback(groupId,replyMsgId,"未知配置: "+key+"\n可用: warnlimit / joincheck / notifygroup");
}
}
public void handleToggle(String groupId,String op,String text,long replyMsgId){
if(!isOwnerOrDelegate(groupId,op)){
sendReplyFeedback(groupId,replyMsgId,"权限不足");
return;
}
String[]parts=text.split("\\s+");
if(parts.length<3){
sendReplyFeedback(groupId,replyMsgId,
"格式错误: /toggle <功能> on|off\n功能: autoban / unmutedelegate");
return;
}
String feature=parts[1].toLowerCase();
String state=parts[2].toLowerCase();
if(!"on".equals(state)&& !"off".equals(state)){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /toggle "+feature+" on|off");
return;
}
Set valid=new HashSet();
valid.add("autoban");
valid.add("unmutedelegate");
if(!valid.contains(feature)){
sendReplyFeedback(groupId,replyMsgId,
"未知功能: "+feature+"\n可用: autoban / unmutedelegate");
return;
}
String nameZh=feature;
if("autoban".equals(feature))nameZh="退群拉黑";
else if("unmutedelegate".equals(feature))nameZh="自动解禁代管";
setGroupConfig(groupId,feature,"on".equals(state)?"on":null);
sendReplyFeedback(groupId,replyMsgId,nameZh+"已"+("on".equals(state)?"开启":"关闭")+" 执行人: [atUin="+op+"]");
writeLog(groupId,op,"/toggle "+feature,"",state);
}
public void handleLook(String groupId,String op,String text,long replyMsgId){
String[]parts=text.split("\\s+");
if(parts.length<2|| !"admin".equalsIgnoreCase(parts[1])){
sendReplyFeedback(groupId,replyMsgId,"格式: /look admin [群号]\n不写群号=本群");
return;
}
String targetGroup=groupId;
if(parts.length>=3){
if(!parts[2].matches("\\d{5,12}")){
sendReplyFeedback(groupId,replyMsgId,"群号格式错误: "+parts[2]);
return;
}
targetGroup=parts[2];
}
if(!targetGroup.equals(groupId)&& !isOwner(op)){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主可跨群查询");
return;
}
sendReplyFeedback(groupId,replyMsgId,renderAdminList(targetGroup,groupId));
}
Map commandHandlers=new HashMap();
interface CommandHandler{
void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId);
}
public void initHandlers(){
commandHandlers.put("/mute",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"禁言失败: 权限不足");
return;
}
if("all".equals(args.subCommand)){
try{
shutUpAll(groupId,true);
sendReplyFeedback(groupId,replyMsgId,"已开启全体禁言 执行人: [atUin="+operatorUin+"]");
writeLog(groupId,operatorUin,"/mute all","","");
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"全体禁言失败: "+e.getMessage());
}
return;
}
if("list".equals(args.subCommand)){
showMuteList(groupId,replyMsgId);
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /mute 用户QQ号或@用户 <时长> [理由]\n也可引用消息或@用户后直接发送 /mute <时长> [理由]\n时长: 数字默认秒(如 114), 可加后缀 s/m/h/d/mth (mth=一个月), 最长 30 天");
return;
}
if(args.timeSeconds<=0){
if(args.timeStr==null){
sendReplyFeedback(groupId,replyMsgId,"缺少时长: 如 114 (秒) / 30s / 15m / 2h / 1d / mth (一个月)");
}else{
sendReplyFeedback(groupId,replyMsgId,"时长超出范围: 需在 1 秒 ~ 30 天之间");
}
return;
}
for(int i=0;i<args.targets.size();i++){
String target=(String)args.targets.get(i);
if(!checkTarget(groupId,operatorUin,target,replyMsgId,"禁言"))continue;
doMute(groupId,operatorUin,target,args.timeSeconds,args.reason,replyMsgId);
}
}
});
commandHandlers.put("/unmute",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"解禁失败: 权限不足");
return;
}
if("all".equals(args.subCommand)){
try{
shutUpAll(groupId,false);
sendReplyFeedback(groupId,replyMsgId,"已关闭全体禁言 执行人: [atUin="+operatorUin+"]");
writeLog(groupId,operatorUin,"/unmute all","","");
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"全体解禁失败: "+e.getMessage());
}
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /unmute @用户 或 /unmute all");
return;
}
for(int i=0;i<args.targets.size();i++){
String target=(String)args.targets.get(i);
if(isOwner(target)){
sendReplyFeedback(groupId,replyMsgId,"解禁失败: 不能操作宿主");
continue;
}
doUnmute(groupId,operatorUin,target,replyMsgId);
}
}
});
commandHandlers.put("/kick",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"踢出失败: 权限不足");
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /kick @用户 [理由]\n也可引用要踢的消息后直接发送 /kick [理由]");
return;
}
for(int i=0;i<args.targets.size();i++){
String target=(String)args.targets.get(i);
if(!checkTarget(groupId,operatorUin,target,replyMsgId,"踢出"))continue;
doKick(groupId,operatorUin,target,args.reason,replyMsgId);
}
}
});
commandHandlers.put("/ban",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"踢黑失败: 权限不足");
return;
}
if("add".equals(args.subCommand)||"setuser".equals(args.subCommand)
||"setgroup".equals(args.subCommand)){
boolean isGroupCmd="setgroup".equals(args.subCommand);
String targetGroup=groupId;
ArrayList qqs=new ArrayList();
if(isGroupCmd){
ArrayList typed=new ArrayList();
if(!args.targetFromQuote){
for(int i=args.atCount;i<args.targets.size();i++){
typed.add(args.targets.get(i));
}
}
if(typed.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,
"格式错误: /ban setgroup <群号> [QQ号...]\n"+
"群号必须写在最前面, 后面的号码只当 QQ 号\n"+
"也可引用一条含号码的消息后发送 /ban setgroup <群号>");
return;
}
targetGroup=(String)typed.get(0);
for(int i=1;i<typed.size();i++)qqs.add(typed.get(i));
for(int i=0;i<args.atCount;i++){
String u=(String)args.targets.get(i);
if(!qqs.contains(u))qqs.add(u);
}
if(qqs.isEmpty()&&args.hasReply&&args.quotedQqs!=null){
qqs=args.quotedQqs;
}
}else{
boolean quotedAsTarget=args.targetFromQuote
||(args.hasReply&&args.targets.size()==1
&&args.quotedUin!=null
&&args.targets.get(0).equals(args.quotedUin));
if(quotedAsTarget){
if(args.quotedQqs!=null)qqs=args.quotedQqs;
if(qqs.isEmpty()&&args.quotedUin!=null)qqs.add(args.quotedUin);
}else if(!args.targets.isEmpty()){
qqs=args.targets;
}else if(args.hasReply&&args.quotedQqs!=null){
qqs=args.quotedQqs;
}
}
if(!targetGroup.equals(groupId)&& !isOwner(operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主可向其他群添加黑名单");
return;
}
if(qqs==null||qqs.isEmpty()){
if(args.hasReply){
sendReplyFeedback(groupId,replyMsgId,
"未识别到 QQ 号: 正文里没有号码, 也没能读到被引用者\n"+
"正文可直接列号码, 或用 #号码# / 【号码】 / 「号码」 / *号码* / <号码> / $号码$ / 〖号码〗 标记\n"+
"支持 换行 / 空格 / , / ， / | / 、 分隔\n"+
"也可直接发: /ban setuser 123456,234567 或 /ban @用户");
}else{
sendReplyFeedback(groupId,replyMsgId,
"请提供要拉黑的 QQ 号\n"+
"本群: /ban setuser 123456,234567,345678\n"+
"跨群: /ban setgroup <群号> <QQ号...> (仅宿主)\n"+
"分隔符支持 换行 / 空格 / , / ， / | / 、");
}
return;
}
StringBuilder fb=new StringBuilder();
int okCount=0;
for(int i=0;i<qqs.size();i++){
String u=(String)qqs.get(i);
String skipReason=null;
if(isOwner(u))skipReason="宿主";
else if(isProtected(targetGroup,u))skipReason="代管";
else if(isBanned(targetGroup,u))skipReason="已在名单";
if(skipReason!=null){
if(fb.length()>0)fb.append("\n");
fb.append("跳过 ").append(u).append("(").append(skipReason).append(")");
continue;
}
addBan(targetGroup,u,operatorUin,args.reason);
okCount=okCount+1;
if(okCount<=10){
if(fb.length()>0)fb.append("\n");
fb.append("已将\"").append(u).append("\"加入");
if(targetGroup.equals(groupId))fb.append("黑名单");
else fb.append("群 ").append(targetGroup).append(" 黑名单");
}
writeLog(targetGroup,operatorUin,"/ban "+args.subCommand,u,
(args.hasReply?"来自引用消息 ":"直接输入 ")+(args.reason==null?"":args.reason));
}
if(okCount>10){
if(fb.length()>0)fb.append("\n");
fb.append("... 共 ").append(okCount).append(" 个已加入");
}
if(fb.length()==0)fb.append("没有需要处理的号码");
sendReplyFeedback(groupId,replyMsgId,fb.toString());
return;
}
boolean isListCmd="list".equals(args.subCommand);
boolean isCheckCmd="check".equals(args.subCommand);
boolean isKickCmd="kick".equals(args.subCommand);
if(isListCmd||isCheckCmd||isKickCmd){
String targetGroup=groupId;
if(!args.targets.isEmpty()){
String g=(String)args.targets.get(0);
if(g.matches("\\d{5,12}"))targetGroup=g;
}
if(!targetGroup.equals(groupId)&& !isOwner(operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主可查看或操作其他群的黑名单");
return;
}
if(isListCmd){
showBanList(groupId,replyMsgId,targetGroup);
}else if(isCheckCmd&&args.quotedQqs!=null&& !args.quotedQqs.isEmpty()){
checkQuotedQqsAgainstBan(groupId,replyMsgId,targetGroup,args.quotedQqs);
}else{
checkBannedMembers(groupId,operatorUin,replyMsgId,targetGroup,isKickCmd);
}
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,
"格式错误: /ban @用户 [理由]\n/ban list|check|kick [群号]\n也可引用消息后直接发送 /ban [理由]");
return;
}
for(int i=0;i<args.targets.size();i++){
String target=(String)args.targets.get(i);
if(!checkTarget(groupId,operatorUin,target,replyMsgId,"踢黑"))continue;
doBan(groupId,operatorUin,target,args.reason,replyMsgId);
}
}
});
commandHandlers.put("/unban",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"解除失败: 权限不足");
return;
}
if(args.targets.isEmpty()&&args.quotedQqs!=null&& !args.quotedQqs.isEmpty()){
StringBuilder fb=new StringBuilder();
for(int i=0;i<args.quotedQqs.size();i++){
String u=(String)args.quotedQqs.get(i);
if(fb.length()>0)fb.append("\n");
if(!isBanned(groupId,u)){
fb.append("跳过 ").append(u).append("(不在名单)");
continue;
}
removeBan(groupId,u);
fb.append("已将\"").append(u).append("\"移除黑名单");
writeLog(groupId,operatorUin,"/unban",u,"来自引用消息");
}
sendReplyFeedback(groupId,replyMsgId,fb.toString());
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /unban @用户\n也可引用一条含 QQ 号的消息后发送 /unban");
return;
}
for(int i=0;i<args.targets.size();i++){
String target=(String)args.targets.get(i);
if(!isBanned(groupId,target)){
sendReplyFeedback(groupId,replyMsgId,nm(groupId,target)+"("+target+") 不在本群黑名单中");
continue;
}
removeBan(groupId,target);
sendReplyFeedback(groupId,replyMsgId,
"已从本群黑名单移除 "+nm(groupId,target)+"("+target+")\n"+
"提示: QQ 侧拉黑需在群设置-群成员中手动解除");
writeLog(groupId,operatorUin,"/unban",target,"");
}
}
});
commandHandlers.put("/warn",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"警告失败: 权限不足");
return;
}
if("list".equals(args.subCommand)){
ArrayList list=getWarnList(groupId);
if(list.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"本群暂无警告记录");
return;
}
int limit=getGroupConfigInt(groupId,"warnlimit",3);
StringBuilder sb=new StringBuilder("警告记录(阈值 "+limit+" 次):\n");
for(int i=0;i<list.size();i++){
String[]p=((String)list.get(i)).split("\\|");
String uin=p.length>0?p[0]:"";
String c=p.length>1?p[1]:"0";
sb.append(i+1).append(". ").append(nm(groupId,uin)).append("(").append(uin).append(") ");
sb.append(c).append("次\n");
}
sendReplyFeedback(groupId,replyMsgId,sb.toString().trim());
return;
}
if("reset".equals(args.subCommand)){
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /warn reset @用户");
return;
}
String target=(String)args.targets.get(0);
resetWarn(groupId,target);
sendReplyFeedback(groupId,replyMsgId,"已清空 "+nm(groupId,target)+"("+target+") 的警告记录");
writeLog(groupId,operatorUin,"/warn reset",target,"");
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /warn @用户 [理由]\n也可引用消息后直接发送 /warn [理由]\n/warn list /warn reset @用户");
return;
}
int limit=getGroupConfigInt(groupId,"warnlimit",3);
for(int i=0;i<args.targets.size();i++){
String target=(String)args.targets.get(i);
if(!checkTarget(groupId,operatorUin,target,replyMsgId,"警告"))continue;
int count=addWarn(groupId,target);
writeLog(groupId,operatorUin,"/warn",target,"第"+count+"次 "+(args.reason==null?"":args.reason));
if(count>=limit){
resetWarn(groupId,target);
try{
kickGroup(groupId,target,false);
sendReplyFeedback(groupId,replyMsgId,
nm(groupId,target)+"("+target+") 警告已达 "+limit+" 次，已自动踢出");
writeLog(groupId,"系统","/warn auto-kick",target,"达"+limit+"次");
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"警告达上限，但踢出失败: "+e.getMessage());
}
}else{
StringBuilder fb=new StringBuilder();
fb.append("已警告 ").append(nm(groupId,target)).append("(").append(target).append(") ");
fb.append("[").append(count).append("/").append(limit).append("]");
if(args.reason!=null&&args.reason.length()>0)fb.append(" 理由: ").append(args.reason);
fb.append(" 执行人: [atUin=").append(operatorUin).append("]");
sendReplyFeedback(groupId,replyMsgId,fb.toString());
}
}
}
});
commandHandlers.put("/admin",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
boolean owner=isOwner(operatorUin);
boolean localMgr= !owner&&containsLine(getAutoAdminFile(groupId),operatorUin);
boolean gOwner= !owner&&isGroupOwner(groupId,operatorUin);
if(!owner&& !localMgr&& !gOwner){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主或本群管理员可操作");
return;
}
boolean crossGroup=false;
String targetGroup=groupId;
ArrayList users=new ArrayList();
if(args.targets.size()>=2){
String first=(String)args.targets.get(0);
if(!first.equals(groupId)&&first.matches("\\d{5,12}")){
crossGroup=true;
targetGroup=first;
for(int i=1;i<args.targets.size();i++){
users.add(args.targets.get(i));
}
}
}
if(!crossGroup){
users=args.targets;
}
if(crossGroup&& !owner){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主可跨群操作");
return;
}
File f=getDelegateFile(targetGroup);
File af=getAutoAdminFile(targetGroup);
File lf=getLocalAdminFile(targetGroup);
if("list".equals(args.subCommand)){
sendReplyFeedback(groupId,replyMsgId,renderAdminList(targetGroup,groupId));
return;
}
if("clear".equals(args.subCommand)){
if(!owner){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主可清空");
return;
}
writeLinesAtomic(f,new ArrayList());
String label=targetGroup.equals(groupId)?"本群":("群 "+targetGroup);
sendReplyFeedback(groupId,replyMsgId,label+"手动代管已清空(群主/管理员自动识别与本地名单不受影响) 执行人: [atUin="+operatorUin+"]");
writeLog(targetGroup,operatorUin,"/admin clear","","");
return;
}
if(args.subCommand==null||(!"add".equals(args.subCommand)&& !"rm".equals(args.subCommand))){
if(owner)sendReplyFeedback(groupId,replyMsgId,"格式: /admin add|rm|list|clear [群号] @用户\n不写群号=本群");
else sendReplyFeedback(groupId,replyMsgId,"格式: /admin add|rm|list @用户\n不写群号=本群");
return;
}
if(users.isEmpty()){
if(owner)sendReplyFeedback(groupId,replyMsgId,"格式: /admin "+args.subCommand+" [群号] @用户");
else sendReplyFeedback(groupId,replyMsgId,"格式: /admin "+args.subCommand+" @用户");
return;
}
StringBuilder fb=new StringBuilder();
for(int i=0;i<users.size();i++){
String target=(String)users.get(i);
if(isOwner(target)){
if(fb.length()>0)fb.append("\n");
fb.append("跳过 ").append(target).append("(宿主)");
continue;
}
if("add".equals(args.subCommand)){
if(containsLine(af,target)){
if(fb.length()>0)fb.append("\n");
fb.append(nm(targetGroup,target)).append("(").append(target).append(") 是群主/管理员, 系统已自动识别");
}else if(containsLine(f,target)){
if(fb.length()>0)fb.append("\n");
fb.append(nm(targetGroup,target)).append("(").append(target).append(") 已是代管");
}else if(containsLine(lf,target)){
if(fb.length()>0)fb.append("\n");
fb.append(nm(targetGroup,target)).append("(").append(target).append(") 已是本地代管");
}else if(owner){
appendLine(f,target);
if(fb.length()>0)fb.append("\n");
fb.append("已添加");
if(targetGroup.equals(groupId))fb.append("本群");
else fb.append("群 ").append(targetGroup);
fb.append("代管: ").append(nm(targetGroup,target)).append("(").append(target).append(")");
writeLog(targetGroup,operatorUin,"/admin add",target,"");
}else{
appendLine(lf,target);
if(fb.length()>0)fb.append("\n");
fb.append("已添加本群本地代管: ").append(nm(targetGroup,target)).append("(").append(target).append(")");
writeLog(targetGroup,operatorUin,"/admin add(local)",target,"");
}
}else{
if(containsLine(f,target)){
if(!owner&& !gOwner){
if(fb.length()>0)fb.append("\n");
fb.append(nm(targetGroup,target)).append("(").append(target).append(") 由宿主添加, 仅宿主或群主可移除");
}else{
removeLine(f,target);
removeLine(lf,target);
if(fb.length()>0)fb.append("\n");
fb.append("已移除");
if(targetGroup.equals(groupId))fb.append("本群");
else fb.append("群 ").append(targetGroup);
fb.append("代管: ").append(nm(targetGroup,target)).append("(").append(target).append(")");
if(containsLine(af,target))fb.append("(其仍为群主/管理员, 系统自动识别中)");
writeLog(targetGroup,operatorUin,"/admin rm",target,"");
}
}else if(containsLine(lf,target)){
removeLine(lf,target);
if(fb.length()>0)fb.append("\n");
fb.append("已从本地名单移除");
if(targetGroup.equals(groupId))fb.append("本群");
else fb.append("群 ").append(targetGroup);
fb.append("代管: ").append(nm(targetGroup,target)).append("(").append(target).append(")");
if(containsLine(af,target))fb.append("(其仍为群主/管理员, 系统自动识别中)");
writeLog(targetGroup,operatorUin,"/admin rm(local)",target,"");
}else if(containsLine(af,target)){
if(fb.length()>0)fb.append("\n");
fb.append(nm(targetGroup,target)).append("(").append(target).append(") 是群主/管理员, 系统自动识别, 无需手动移除");
}else{
if(fb.length()>0)fb.append("\n");
fb.append(nm(targetGroup,target)).append("(").append(target).append(") 不是代管");
}
}
}
sendReplyFeedback(groupId,replyMsgId,fb.toString());
}
});
commandHandlers.put("/alliance",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwner(operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主可管理联盟");
return;
}
if("add".equals(args.subCommand)){
if(isAllianceGroup(groupId)){
sendReplyFeedback(groupId,replyMsgId,"本群已在联盟中");
return;
}
addAllianceGroup(groupId);
sendReplyFeedback(groupId,replyMsgId,"已加入联盟: "+groupId+" 执行人: [atUin="+operatorUin+"]");
writeLog(groupId,operatorUin,"/alliance add","",groupId);
}else if("rm".equals(args.subCommand)){
if(!isAllianceGroup(groupId)){
sendReplyFeedback(groupId,replyMsgId,"本群不在联盟中");
return;
}
removeAllianceGroup(groupId);
sendReplyFeedback(groupId,replyMsgId,"已退出联盟: "+groupId+" 执行人: [atUin="+operatorUin+"]");
writeLog(groupId,operatorUin,"/alliance rm","",groupId);
}else if("list".equals(args.subCommand)){
ArrayList gs=getAllianceGroups();
if(gs.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"联盟群列表为空");
return;
}
StringBuilder sb=new StringBuilder("联盟群("+gs.size()+"个):\n");
for(int i=0;i<gs.size();i++){
String g=(String)gs.get(i);
sb.append(i+1).append(". ").append(g);
if(g.equals(groupId))sb.append(" (本群)");
sb.append("\n");
}
sendReplyFeedback(groupId,replyMsgId,sb.toString().trim());
}else{
sendReplyFeedback(groupId,replyMsgId,"格式错误: /alliance add|rm|list");
}
}
});
commandHandlers.put("/fban",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"联盟封禁失败: 权限不足");
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /fban @用户 [理由]");
return;
}
String target=(String)args.targets.get(0);
if(!checkTarget(groupId,operatorUin,target,replyMsgId,"联盟封禁"))return;
if(isFbanUser(target)){
sendReplyFeedback(groupId,replyMsgId,"该用户已在联盟封禁名单中");
return;
}
try{
doFban(groupId,target,args.reason,operatorUin);
}catch(Exception e){
sendReplyFeedback(groupId,replyMsgId,"联盟封禁失败: "+e.getMessage());
return;
}
StringBuilder fb=new StringBuilder();
fb.append("联盟封禁 ").append(nm(groupId,target)).append("(").append(target).append(")");
if(args.reason!=null&&args.reason.length()>0)fb.append(" 理由: ").append(args.reason);
fb.append(" 已通知 ").append(getAllianceGroups().size()).append(" 个联盟群");
fb.append(" 执行人: [atUin=").append(operatorUin).append("]");
sendReplyFeedback(groupId,replyMsgId,fb.toString());
}
});
commandHandlers.put("/unfban",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"解除封禁失败: 权限不足");
return;
}
if(args.targets.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"格式错误: /unfban @用户 [原因]");
return;
}
String target=(String)args.targets.get(0);
if(!isFbanUser(target)){
sendReplyFeedback(groupId,replyMsgId,"该用户未被联盟封禁");
return;
}
String oldReason=getFbanReason(target);
removeFbanUser(target);
StringBuilder fb=new StringBuilder();
fb.append("解除联盟封禁 ").append(nm(groupId,target)).append("(").append(target).append(")");
if(args.reason!=null&&args.reason.length()>0)fb.append(" 原因: ").append(args.reason);
fb.append(" 原封禁理由: ").append(oldReason.length()>0?oldReason:"无");
fb.append(" 执行人: [atUin=").append(operatorUin).append("]");
sendReplyFeedback(groupId,replyMsgId,fb.toString());
writeLog(groupId,operatorUin,"/unfban",target,args.reason);
}
});
commandHandlers.put("/status",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwnerOrDelegate(groupId,operatorUin)){
sendReplyFeedback(groupId,replyMsgId,"权限不足");
return;
}
String shutAll="未知";
try{shutAll=isShutUp(groupId)?"是":"否";}catch(Exception e){}
StringBuilder sb=new StringBuilder();
sb.append("群状态\n");
sb.append("退群拉黑: ").append(isEnabled(groupId,"autoban")?"开":"关").append("\n");
sb.append("入群处理: ").append(joinCheckZh(getGroupConfig(groupId,"joincheck","ask"))).append("\n");
String ng=getGroupConfig(groupId,"notifygroup","");
sb.append("通知群: ").append(ng.length()>0?ng:"未设置").append("\n");
sb.append("自动解禁代管: ").append(isEnabled(groupId,"unmutedelegate")?"开":"关").append("\n");
sb.append("警告阈值: ").append(getGroupConfigInt(groupId,"warnlimit",3)).append(" 次\n");
sb.append("联盟群: ").append(isAllianceGroup(groupId)?"是":"否").append("\n");
sb.append("全员禁言: ").append(shutAll).append("\n");
ArrayList mDels=readLines(getDelegateFile(groupId));
ArrayList aDels=readLines(getAutoAdminFile(groupId));
int autoExtra=0;
for(int i=0;i<aDels.size();i++){
if(!mDels.contains(aDels.get(i)))autoExtra=autoExtra+1;
}
ArrayList lDels=readLines(getLocalAdminFile(groupId));
sb.append("群管权: ").append(mDels.size()+autoExtra).append("(现管人数)+").append(lDels.size()).append("(代管人数)");
sb.append("\n");
sb.append("黑名单: ").append(getBanList(groupId).size()).append(" 人");
sendReplyFeedback(groupId,replyMsgId,sb.toString());
}
});
commandHandlers.put("/on",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwner(operatorUin))return;
if(isSessionEnabled(groupId)){
syncGroupAdmins(groupId);
sendReplyFeedback(groupId,replyMsgId,"本群群管已启用，已刷新群主/管理员名单 执行人: [atUin="+operatorUin+"]");
return;
}
setSessionEnabled(groupId,true);
syncGroupAdmins(groupId);
sendReplyFeedback(groupId,replyMsgId,"本群群管已启用 执行人: [atUin="+operatorUin+"]");
writeLog(groupId,operatorUin,"/on","","");
}
});
commandHandlers.put("/off",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
if(!isOwner(operatorUin))return;
if(!isSessionEnabled(groupId)){
sendReplyFeedback(groupId,replyMsgId,"本群群管未启用");
return;
}
setSessionEnabled(groupId,false);
sendReplyFeedback(groupId,replyMsgId,"本群群管已禁用 执行人: [atUin="+operatorUin+"]");
}
});
commandHandlers.put("/help",new CommandHandler(){
public void handle(String groupId,String operatorUin,CommandArgs args,long replyMsgId){
boolean admin=isOwnerOrDelegate(groupId,operatorUin);
boolean owner=isOwner(operatorUin);
StringBuilder sb=new StringBuilder();
if(!admin){
sb.append("本群已启用群管\n");
sb.append("你的身份: 普通成员\n");
sb.append("可用指令: /help | /look admin 查看本群管理权限名单\n");
sb.append("禁言/踢出等操作需要代管或宿主权限, 请联系管理员\n");
sb.append("提示: 管理员引用一条消息即可对该成员执行操作");
sendReplyFeedback(groupId,replyMsgId,sb.toString());
return;
}
sb.append("群管指令 (引用一条消息可代替 @用户)\n");
sb.append("\n-- 基础 --\n");
sb.append("/mute @用户 <时长> [理由] 禁言\n");
sb.append("/mute all 全体禁言 | /mute list 禁言列表\n");
sb.append("/unmute @用户 | /unmute all\n");
sb.append("/kick @用户 [理由] 踢出\n");
sb.append("/ban @用户 [理由] 踢黑\n");
sb.append("/help 本帮助\n");
sb.append("\n-- 管理员 --\n");
sb.append("/unban @用户 解除踢黑\n");
if(owner){
sb.append("/ban setuser <QQ号...> 拉黑本群 QQ 号(引用或直接列)\n");
sb.append("/ban setgroup <群号> <QQ号...> 跨群拉黑(仅宿主)\n");
sb.append("/ban list [群号] 查看黑名单, 可跨群\n");
sb.append("/ban check [群号] 核对本群成员\n");
sb.append("/ban kick [群号] 一键踢出命中\n");
}else{
sb.append("/ban setuser <QQ号...> 拉黑本群 QQ 号(引用或直接列)\n");
sb.append("/ban list 查看本群黑名单\n");
sb.append("/ban check 核对本群成员\n");
sb.append("/ban kick 一键踢出命中\n");
}
sb.append("/look admin"+(owner?" [群号]":"")+" 查看管理权限名单\n");
sb.append("/warn @用户 [理由] 警告\n");
sb.append("/warn list | /warn reset @用户\n");
sb.append("/fban @用户 [理由] 联盟封禁\n");
sb.append("/unfban @用户 [原因] 解除封禁\n");
sb.append("/status 查看本群配置\n");
sb.append("/toggle autoban on|off 退群自动拉黑\n");
sb.append("/toggle unmutedelegate on|off 代管被禁言自动解禁\n");
sb.append("/set <键> <值>\n");
if(owner){
sb.append("\n-- 宿主 --\n");
sb.append("/admin add|rm|list|clear [群号] @用户\n");
sb.append("/alliance add|rm|list\n");
sb.append("/on 启用本群群管 | /off 禁用\n");
}else if(containsLine(getAutoAdminFile(groupId),operatorUin)){
sb.append("\n-- 群管维护 --\n");
sb.append("/admin add|rm|list @用户 维护本群本地代管名单\n");
}
sb.append("\n时长: 数字默认秒, 可加 s/m/h/d/mth (最长 30 天)\n");
sb.append("toggle: autoban unmutedelegate\n");
sb.append("set: warnlimit joincheck notifygroup\n");
sb.append("宿主设置: 在 config/owner.txt 写入 QQ 号");
sendReplyFeedback(groupId,replyMsgId,sb.toString());
}
});
}
public String joinCheckZh(String v){
if("kick".equals(v))return"直接踢出";
if("off".equals(v))return"不处理";
return"询问管理员";
}
public void notifyJoinHit(String groupId,String userUin,String source,String reason){
String ng=getGroupConfig(groupId,"notifygroup","");
boolean hasNg=ng!=null&&ng.length()>0;
String also=(hasNg&& !ng.equals(groupId))?ng:null;
String t=banTimeOf(groupId,userUin);
String head="入群提醒\n"
+"成员: "+nm(groupId,userUin)+"("+userUin+")\n"
+"命中: ";
String tail=" | 时间: "+(t.length()>0?t:"未知")
+" | 理由: "+(reason!=null&&reason.length()>0?reason:"无")
+"\n处理: 是否直接踢出? 回复 yes 一键踢出 (60 秒内有效)";
StringBuilder mentions=new StringBuilder();
if(ownerUin!=null&&ownerUin.length()>0&& !ownerUin.equals(myUin)){
mentions.append(" [atUin=").append(ownerUin).append("]");
}
ArrayList dels=getAllDelegates(groupId);
int n=0;
for(int i=0;i<dels.size()&&n<5;i++){
String d=String.valueOf(dels.get(i));
if(d.equals(myUin)||d.equals(ownerUin))continue;
mentions.append(" [atUin=").append(d).append("]");
n=n+1;
}
if(mentions.length()>0)tail=tail+"\n提醒:"+mentions.toString();
java.util.Map pk=new java.util.HashMap();
ArrayList hitsList=new ArrayList();
hitsList.add(userUin);
pk.put("hits",hitsList);
pk.put("misses",new ArrayList());
pk.put("target",groupId);
pk.put("alsoKey",also);
if(also!=null){
pendingCheckData.put(also,pk);
pendingCheckTime.put(also,Long.valueOf(System.currentTimeMillis()));
String gname=groupNameOf(groupId);
String fwdLabel=(gname.length()>0?gname+"("+groupId+")":groupId)+" 黑名单";
try{
sendMsg(also,head+fwdLabel+tail,2);
}catch(Exception e){
dbg("notifyGroup: "+e);
}
}else{
pendingCheckData.put(groupId,pk);
pendingCheckTime.put(groupId,Long.valueOf(System.currentTimeMillis()));
sendFeedback(groupId,head+source+tail);
}
}
public String groupNameOf(String groupId){
try{
Object gi=getGroupInfo(groupId);
if(gi==null)return"";
Object v=optField(gi,"troopNameFromNT");
if(v==null||String.valueOf(v).length()==0)v=optField(gi,"troopname");
if(v==null||String.valueOf(v).length()==0)v=optField(gi,"troopName");
return v==null?"":String.valueOf(v);
}catch(Exception e){
return"";
}
}
public void clearPendingCheck(String groupId){
try{
Object d=pendingCheckData.get(groupId);
if(d instanceof Map){
Object also=((Map)d).get("alsoKey");
if(also!=null){
pendingCheckData.remove(also);
pendingCheckTime.remove(also);
}
}
pendingCheckData.remove(groupId);
pendingCheckTime.remove(groupId);
}catch(Exception e){
dbg("clearPending: "+e);
}
}
public void joinGroup(String groupId,String userUin){
try{
if(userUin==null||isOwner(userUin))return;
if(!isSessionEnabled(groupId))return;
String jc=getGroupConfig(groupId,"joincheck","ask");
if(isBanned(groupId,userUin)){
if("off".equals(jc)){
writeLog(groupId,"系统","/joinhit",userUin,"命中本群黑名单, 已按配置跳过");
}else if("kick".equals(jc)){
kickGroup(groupId,userUin,true);
sendFeedback(groupId,"已拦截黑名单用户: "+userUin);
writeLog(groupId,"系统","/block",userUin,"命中本群黑名单, 直接踢出");
}else{
notifyJoinHit(groupId,userUin,"本群黑名单",banReasonOf(groupId,userUin));
writeLog(groupId,"系统","/joinhit",userUin,"命中本群黑名单, 已询问管理员");
}
return;
}
if(isAllianceGroup(groupId)&&isFbanUser(userUin)){
kickGroup(groupId,userUin,true);
sendFeedback(groupId,"已拦截联盟封禁用户: "+userUin);
writeLog(groupId,"系统","/block",userUin,"命中联盟封禁");
return;
}
writeLog(groupId,"系统","/join",userUin,"");
}catch(Exception e){
dbg("joinGroupError: "+e);
}
}
public void quitGroup(String groupId,String userUin){
try{
if(userUin==null||isOwner(userUin))return;
if(!isSessionEnabled(groupId))return;
writeLog(groupId,"系统","/quit",userUin,"");
if(isEnabled(groupId,"autoban")){
if(!isBanned(groupId,userUin)){
addBan(groupId,userUin,"系统","退群自动拉黑");
writeLog(groupId,"系统","/autoban",userUin,"退群自动拉黑");
}
}
if(containsLine(getAutoAdminFile(groupId),userUin)){
syncGroupAdmins(groupId);
}
}catch(Exception e){
dbg("quitGroupError: "+e);
}
}
public void shutUpGroup(String groupId,String userUin,long time,String opUin){
try{
if(userUin==null||isOwner(userUin))return;
if(!isSessionEnabled(groupId))return;
boolean external=(opUin==null)||(!opUin.equals(myUin));
String who=(opUin==null||opUin.length()==0)?"系统":opUin;
if(time<=0L){
if(external)writeLog(groupId,who,"/external-unmute",userUin,"");
return;
}
if(external){
writeLog(groupId,who,"/external-mute",userUin,formatTime((int)time));
}
if(!isEnabled(groupId,"unmutedelegate"))return;
if(isDelegate(groupId,userUin)){
shutUp(groupId,userUin,0L);
sendFeedback(groupId,"检测到代管被禁言，已自动解禁 "+nm(groupId,userUin));
writeLog(groupId,"系统","/auto-unmute",userUin,"被 "+who+" 禁言");
}
}catch(Exception e){
dbg("shutUpGroupError: "+e);
}
}
public void onMsg(Object msg){
if(msg==null)return;
String text=msg.msg;
if(text==null)return;
if(msg.type!=2)return;
String senderUin=String.valueOf(msg.userUin);
if(senderUin==null||senderUin.length()==0||"0".equals(senderUin)||"null".equals(senderUin))return;
String groupId=String.valueOf(msg.peerUin);
if(groupId==null||groupId.length()==0||"null".equals(groupId))return;
final long replyMsgId=msg.msgId;
String trimmed=text.trim();
if(trimmed.length()==0)return;
if("yes".equalsIgnoreCase(trimmed)||"add".equalsIgnoreCase(trimmed)){
Object pkObj=pendingCheckData.get(groupId);
java.util.Map pkMap=null;
if(pkObj instanceof Map)pkMap=(Map)pkObj;
String pkTarget=groupId;
if(pkMap!=null){
Object tv=pkMap.get("target");
if(tv!=null&&String.valueOf(tv).length()>0)pkTarget=String.valueOf(tv);
}
boolean pkCan=(pkMap!=null)&&(isOwnerOrDelegate(groupId,senderUin)||isOwnerOrDelegate(pkTarget,senderUin));
if(pkCan){
Long ts=(Long)pendingCheckTime.get(groupId);
boolean expired=(ts==null||(System.currentTimeMillis()-ts.longValue())>CHECK_PENDING_TTL);
if(expired){
clearPendingCheck(groupId);
sendReplyFeedback(groupId,replyMsgId,pkTarget.equals(groupId)?"确认已超时，请重新发送 /ban check":"确认已超时，已放弃本次踢出");
return;
}
java.util.Map data=pkMap;
String targetGroup=pkTarget;
boolean isYes="yes".equalsIgnoreCase(trimmed);
if(isYes){
ArrayList hits=(ArrayList)data.get("hits");
if(hits==null||hits.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"没有需要踢出的成员");
clearPendingCheck(groupId);
return;
}
int ok=0;
StringBuilder detail=new StringBuilder();
for(int i=0;i<hits.size();i++){
String uin=(String)hits.get(i);
try{
kickGroup(targetGroup,uin,true);
ok=ok+1;
writeLog(targetGroup,senderUin,"/ban check -> kick",uin,"命中黑名单");
if(detail.length()>0)detail.append("\n");
detail.append("\"").append(uin).append("\"");
}catch(Exception e){
dbg("checkYesKick: "+e);
}
}
clearPendingCheck(groupId);
String out=targetGroup.equals(groupId)
?("已将 "+detail.toString()+" 移出群聊")
:("已将 "+detail.toString()+" 移出群 "+targetGroup);
sendReplyFeedback(groupId,replyMsgId,out);
return;
}else{
ArrayList misses=(ArrayList)data.get("misses");
if(misses==null||misses.isEmpty()){
sendReplyFeedback(groupId,replyMsgId,"没有需要添加的成员");
clearPendingCheck(groupId);
return;
}
if(!targetGroup.equals(groupId)&& !isOwner(senderUin)){
sendReplyFeedback(groupId,replyMsgId,"权限不足: 仅宿主可向其他群添加黑名单");
clearPendingCheck(groupId);
return;
}
StringBuilder fb=new StringBuilder();
for(int i=0;i<misses.size();i++){
String u=(String)misses.get(i);
String skipReason=null;
if(isOwner(u))skipReason="宿主";
else if(isProtected(targetGroup,u))skipReason="代管";
else if(isBanned(targetGroup,u))skipReason="已在名单";
if(fb.length()>0)fb.append("\n");
if(skipReason!=null){
fb.append("跳过 ").append(u).append("(").append(skipReason).append(")");
continue;
}
addBan(targetGroup,u,senderUin,"来自 /ban check add");
fb.append("已将\"").append(u).append("\"加入");
if(targetGroup.equals(groupId))fb.append("黑名单");
else fb.append("群 ").append(targetGroup).append(" 黑名单");
writeLog(targetGroup,senderUin,"/ban check -> add",u,"来自 /ban check add");
}
clearPendingCheck(groupId);
sendReplyFeedback(groupId,replyMsgId,fb.toString());
return;
}
}else if(isOwnerOrDelegate(groupId,senderUin)){
sendReplyFeedback(groupId,replyMsgId,"没有待确认的操作");
return;
}
}
if(!isSessionEnabled(groupId)){
boolean allow="/on".equals(trimmed)||trimmed.equals("/master")||trimmed.startsWith("/master ");
if(!allow&&isOwner(senderUin)&&("/look".equals(trimmed)||trimmed.startsWith("/look ")))allow=true;
if(!allow)return;
}
if(trimmed.startsWith("/")){
if("/set".equals(trimmed)||trimmed.startsWith("/set ")){
handleSet(groupId,senderUin,trimmed,replyMsgId);
return;
}
if("/toggle".equals(trimmed)||trimmed.startsWith("/toggle ")){
handleToggle(groupId,senderUin,trimmed,replyMsgId);
return;
}
if("/look".equals(trimmed)||trimmed.startsWith("/look ")){
handleLook(groupId,senderUin,trimmed,replyMsgId);
return;
}
if(trimmed.equals("/master")||trimmed.startsWith("/master ")){
String[]mp=trimmed.split("\\s+");
String sub=(mp.length>=2)?mp[1].toLowerCase():"";
String u=(mp.length>=3)?mp[2].trim():"";
if(sub.length()==0||"help".equals(sub)){
if(!isListMaster(senderUin))return;
sendReplyFeedback(groupId,replyMsgId,"master 指令 (隐藏, 仅宿主)\n/master add <QQ> 添加到 master 名单\n/master rm <QQ> 从 master 名单移除\n/master help 本帮助");
return;
}
if("add".equals(sub)||"rm".equals(sub)){
if(!isListMaster(senderUin)){
sendReplyFeedback(groupId,replyMsgId,"权限不足");
return;
}
if("add".equals(sub)){
if(!u.matches("\\d{5,12}")){
sendReplyFeedback(groupId,replyMsgId,"格式: /master add <QQ>");
return;
}
if(materList.contains(u)){
sendReplyFeedback(groupId,replyMsgId,"已在 master 名单");
return;
}
materList.add(u);
File mf=new File(getConfigDir()+"/master.txt");
writeLinesAtomic(mf,materList);
sendReplyFeedback(groupId,replyMsgId,"已添加 "+u+" 到 master 名单");
return;
}
if(u.length()==0){
sendReplyFeedback(groupId,replyMsgId,"格式: /master rm <QQ>");
return;
}
if(!materList.contains(u)){
sendReplyFeedback(groupId,replyMsgId,"不在 master 名单");
return;
}
materList.remove(u);
File mf=new File(getConfigDir()+"/master.txt");
writeLinesAtomic(mf,materList);
sendReplyFeedback(groupId,replyMsgId,"已从 master 名单移除 "+u);
return;
}
}
ArrayList atList=new ArrayList();
try{
if(msg.atList!=null)atList=new ArrayList(msg.atList);
}catch(Exception e){
dbg("atListError: "+e);
}
String cmdText=trimmed;
try{
Object atMapRaw=optField(msg,"atMap");
if(atMapRaw instanceof Map&&atList.size()>0){
Map atMap=(Map)atMapRaw;
boolean changed=false;
for(int i=0;i<atList.size();i++){
String u=atList.get(i).toString();
Object v=atMap.get(u);
if(v==null)continue;
String s=v.toString();
if(s.length()>1&&s.startsWith("@")){
cmdText=cmdText.replace(s," ");
changed=true;
}
}
if(changed){
cmdText=cmdText.replaceAll("\\s+"," ").trim();
if(cmdText.length()==0)cmdText=trimmed;
}
}
}catch(Exception e){
dbg("atMapError: "+e);
}
String cmdSource=cmdText;
if(cmdText.startsWith("/ban setuser")||cmdText.startsWith("/ban add")
||cmdText.startsWith("/ban setgroup")||cmdText.startsWith("/unban")){
cmdSource=cmdText.replace(',',' ').replace('，',' ')
.replace('|',' ').replace('、',' ')
.replace('#',' ').replace('＃',' ');
}
CommandArgs args=parseCommand(cmdSource,atList);
if(args.hasError)return;
if(needsTarget(args.command)){
Object repEl=getReplyElement(msg);
args.hasReply=(repEl!=null);
String quoted=null;
if(repEl!=null){
quoted=pickUin(repEl);
if(quoted!=null&&(quoted.length()==0||quoted.equals(myUin)))quoted=null;
args.quotedUin=quoted;
}
boolean isBanAdd="/ban".equals(args.command)
&&("add".equals(args.subCommand)||"setuser".equals(args.subCommand)
||"setgroup".equals(args.subCommand));
boolean isBanCheck="/ban".equals(args.command)&&"check".equals(args.subCommand);
if(isBanAdd||isBanCheck||"/unban".equals(args.command)){
args.quotedQqs=getQuotedQqs(msg,quoted);
if(isBanAdd&&args.hasReply&&args.quotedQqs.isEmpty()){
dbg("banAdd: 引用正文未提取到 QQ, 元素字段="+dumpFields(repEl));
dbg("banAdd: 引用 uid/uin 候选值="+dumpUidValues(repEl));
}
}
boolean noQq=(args.quotedQqs==null||args.quotedQqs.isEmpty());
if(args.targets.isEmpty()&&noQq&&quoted!=null){
args.targets.add(quoted);
args.targetFromQuote=true;
}
}
CommandHandler handler=(CommandHandler)commandHandlers.get(args.command);
if(handler==null)return;
handler.handle(groupId,senderUin,args,replyMsgId);
return;
}
}
public void unLoadPlugin(){
try{
groupCfgCache.clear();
globalCfgCache.clear();
nameCache.clear();
nameCacheTs.clear();
enabledSessionCache=null;
}catch(Exception e){}
}
public void migrateLegacyDelegates(){
try{
File legacy=new File(getConfigDir()+"/delegates/list.txt");
if(!legacy.exists())return;
ArrayList users=readLines(legacy);
if(users.isEmpty()){
legacy.delete();
return;
}
ArrayList groups=readLines(getEnabledFile());
for(int i=0;i<groups.size();i++){
String gid=(String)groups.get(i);
File gf=getDelegateFile(gid);
ArrayList exist=readLines(gf);
for(int j=0;j<users.size();j++){
String u=(String)users.get(j);
if(!exist.contains(u))exist.add(u);
}
writeLinesAtomic(gf,exist);
}
File bak=new File(getConfigDir()+"/delegates/list.txt.migrated");
if(bak.exists())bak.delete();
legacy.renameTo(bak);
dbg("legacy delegates migrated to "+groups.size()+" groups");
}catch(Exception e){
dbg("migrateError: "+e);
}
}
initHandlers();
migrateLegacyDelegates();
loadOwnerUin();
loadMaterList();
try{
ArrayList groups=readLines(getEnabledFile());
for(int i=0;i<groups.size();i++){
syncGroupAdmins((String)groups.get(i));
}
}catch(Exception e){dbg("syncAllAdmin: "+e);}
dbg("Regent-DREX v4.3 loaded, pluginPath="+pluginPath);
