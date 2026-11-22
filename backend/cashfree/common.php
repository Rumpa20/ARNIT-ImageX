<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
$config=require __DIR__.'/config.php';
function out(int $s,array $v):never{http_response_code($s);echo json_encode($v);exit;}
function method(string $m):void{if($_SERVER['REQUEST_METHOD']!==$m)out(405,['error'=>'method_not_allowed']);}
function uid():string {
  $h=$_SERVER['HTTP_AUTHORIZATION']??'';
  if(!preg_match('/^Bearer\s+(.+)$/i',$h,$m))out(401,['error'=>'authentication_required']);
  try { return verify_firebase_token(trim($m[1])); } catch (Throwable $e) { out(401,['error'=>'invalid_auth_token']); }
}
function verify_firebase_token(string $token):string {
  global $config;
  $project=trim((string)($config['firebase_project_id']??''));
  if($project===''||$token==='')out(503,['error'=>'firebase_auth_not_configured']);
  if(!is_file(__DIR__.'/vendor/autoload.php'))out(503,['error'=>'server_dependencies_missing']);
  require_once __DIR__.'/vendor/autoload.php';
  $parts=explode('.', $token);
  if(count($parts)!==3)out(401,['error'=>'invalid_auth_token']);
  $header=json_decode(base64_decode(strtr($parts[0],'-_','+/')),true);
  if(!is_array($header)||($header['alg']??'')!=='RS256'||empty($header['kid']))out(401,['error'=>'invalid_auth_token']);
  $certs=firebase_certs();
  $kid=$header['kid'];
  if(!isset($certs[$kid]))out(401,['error'=>'invalid_auth_token']);
  try {
    $decoded=Firebase\JWT\JWT::decode($token,new Firebase\JWT\Key($certs[$kid],'RS256'));
  } catch(Throwable $e) { out(401,['error'=>'invalid_auth_token']); }
  $now=time();
  if(($decoded->aud??null)!==$project || ($decoded->iss??null)!=='https://securetoken.google.com/'.$project ||
     empty($decoded->sub) || !is_string($decoded->sub) || strlen($decoded->sub)>128 ||
     !isset($decoded->iat) || (int)$decoded->iat>$now+60 || !isset($decoded->exp) || (int)$decoded->exp<=$now) out(401,['error'=>'invalid_auth_token']);
  return (string)$decoded->sub;
}
function firebase_certs():array {
  $url='https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
  $cache= sys_get_temp_dir().'/arnit-imagex-firebase-certs.json';
  if(is_file($cache)&&filemtime($cache)>time()-3600){$saved=json_decode((string)file_get_contents($cache),true);if(is_array($saved))return $saved;}
  $ctx=stream_context_create(['http'=>['timeout'=>8,'header'=>"Accept: application/json\r\n"]]);
  $json=@file_get_contents($url,false,$ctx);
  $certs=json_decode((string)$json,true);
  if(!is_array($certs)||!$certs)out(503,['error'=>'auth_verification_unavailable']);
  @file_put_contents($cache,json_encode($certs),LOCK_EX);
  return $certs;
}
function db():PDO{global $config;$d=dirname($config['db_path']);if(!is_dir($d)&&!mkdir($d,0700,true))out(500,['error'=>'storage_unavailable']);$p=new PDO('sqlite:'.$config['db_path'],null,null,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);$p->exec('CREATE TABLE IF NOT EXISTS orders(order_id TEXT PRIMARY KEY,user_id TEXT,plan TEXT,amount REAL,currency TEXT,status TEXT DEFAULT "PENDING",payment_id TEXT,created_at TEXT,expires_at TEXT)');$p->exec('CREATE TABLE IF NOT EXISTS entitlements(user_id TEXT PRIMARY KEY,plan TEXT,expires_at TEXT,updated_at TEXT)');return $p;}
function cf(string $m,string $path,?array $payload=null):array{global $config;if(!$config['app_id']||!$config['secret'])out(503,['error'=>'cashfree_not_configured']);$ch=curl_init(($config['environment']==='production'?'https://api.cashfree.com/pg':'https://sandbox.cashfree.com/pg').$path);$h=['Content-Type: application/json','x-client-id: '.$config['app_id'],'x-client-secret: '.$config['secret'],'x-api-version: '.$config['api_version']];curl_setopt_array($ch,[CURLOPT_CUSTOMREQUEST=>$m,CURLOPT_HTTPHEADER=>$h,CURLOPT_RETURNTRANSFER=>true,CURLOPT_TIMEOUT=>20]);if($payload!==null)curl_setopt($ch,CURLOPT_POSTFIELDS,json_encode($payload));$b=curl_exec($ch);$code=(int)curl_getinfo($ch,CURLINFO_HTTP_CODE);curl_close($ch);$j=json_decode((string)$b,true);if($code<200||$code>=300||!is_array($j))out(502,['error'=>'cashfree_api_error','status'=>$code]);return $j;}
