<?php
require __DIR__.'/common.php';
method('POST');
$raw=file_get_contents('php://input');
$sig=$_SERVER['HTTP_X_WEBHOOK_SIGNATURE']??'';
$ts=$_SERVER['HTTP_X_WEBHOOK_TIMESTAMP']??'';
if(!$config['webhook_secret']||!$sig||!$ts)out(401,['error'=>'webhook_headers_missing']);
$expected=base64_encode(hash_hmac('sha256',$ts.$raw,$config['webhook_secret'],true));
if(!hash_equals($expected,$sig))out(401,['error'=>'invalid_signature']);
$evt=json_decode($raw,true);
$orderId=$evt['data']['order']['order_id']??$evt['data']['order_id']??'';
if(!$orderId)out(400,['error'=>'missing_order_id']);
$pdo=db();
$q=$pdo->prepare('SELECT * FROM orders WHERE order_id=?');
$q->execute([$orderId]);
$o=$q->fetch(PDO::FETCH_ASSOC);
if(!$o)out(200,['ignored'=>true]);
// Idempotency: do not reset expiry or grant twice for already-paid orders.
if($o['status']==='PAID')out(200,['received'=>true,'already_processed'=>true]);
if(($evt['type']??'')==='PAYMENT_SUCCESS_WEBHOOK'){
 $fresh=cf('GET','/orders/'.$orderId);
 if(($fresh['order_status']??'')==='PAID'){
  $payments=cf('GET','/orders/'.$orderId.'/payments');
  $validPayment=null;
  foreach($payments as $payment){
   $amount=(float)($payment['payment_amount']??0);
   if(($payment['payment_status']??'')==='SUCCESS' && abs($amount-(float)$o['amount'])<0.00001){$validPayment=$payment;break;}
  }
  if($validPayment!==null){
   $plan=$config['plans'][$o['plan']]??null;
   if(!$plan)out(500,['error'=>'plan_configuration_invalid']);
   $expiry=$plan['days']===null?null:gmdate('c',time()+$plan['days']*86400);
   $pid=(string)($validPayment['cf_payment_id']??'');
   $pdo->beginTransaction();
   try{
    $updated=$pdo->prepare('UPDATE orders SET status="PAID",payment_id=?,expires_at=? WHERE order_id=? AND status<>"PAID"');
    $updated->execute([$pid,$expiry,$orderId]);
    if($updated->rowCount()!==1){
     $pdo->rollBack();
     out(200,['received'=>true,'already_processed'=>true]);
    }
    $pdo->prepare('INSERT INTO entitlements(user_id,plan,expires_at,updated_at) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET plan=excluded.plan,expires_at=excluded.expires_at,updated_at=excluded.updated_at')->execute([$o['user_id'],$o['plan'],$expiry,gmdate('c')]);
    $pdo->commit();
   }catch(Throwable $e){
    if($pdo->inTransaction())$pdo->rollBack();
    out(500,['error'=>'entitlement_update_failed']);
   }
  }
 }
}
out(200,['received'=>true]);
