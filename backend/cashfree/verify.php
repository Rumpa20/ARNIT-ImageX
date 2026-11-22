<?php
require __DIR__.'/common.php';
method('POST');
$u=uid();
$b=json_decode(file_get_contents('php://input'),true)?: [];
$id=$b['order_id']??'';
if(!preg_match('/^ix_[a-f0-9]{24}$/',$id))out(400,['error'=>'invalid_order']);
$pdo=db();
$q=$pdo->prepare('SELECT * FROM orders WHERE order_id=? AND user_id=?');
$q->execute([$id,$u]);
$o=$q->fetch(PDO::FETCH_ASSOC);
if(!$o)out(404,['error'=>'order_not_found']);

// Idempotent: a paid order must never re-grant or reset its entitlement expiry.
if($o['status']==='PAID'){
 $e=$pdo->prepare('SELECT plan,expires_at FROM entitlements WHERE user_id=?');
 $e->execute([$u]);
 $ent=$e->fetch(PDO::FETCH_ASSOC);
 out(200,['status'=>'PAID','premium_unlocked'=>true,'already_processed'=>true,'plan'=>$ent['plan']??$o['plan'],'expires_at'=>$ent['expires_at']??$o['expires_at']]);
}
$cfOrder=cf('GET','/orders/'.$id);
if(($cfOrder['order_status']??'')!=='PAID')out(200,['status'=>$cfOrder['order_status']??'UNKNOWN','premium_unlocked'=>false]);
$payments=cf('GET','/orders/'.$id.'/payments');
$validPayment=null;
foreach($payments as $payment){
 if(($payment['payment_status']??'')==='SUCCESS' && abs((float)($payment['payment_amount']??0)-(float)$o['amount'])<0.00001){$validPayment=$payment;break;}
}
if($validPayment===null)out(200,['status'=>'not_verified','premium_unlocked'=>false]);
$p=$config['plans'][$o['plan']]??null;
if(!$p)out(500,['error'=>'plan_configuration_invalid']);
$expiry=$p['days']===null?null:gmdate('c',time()+$p['days']*86400);
$pid=(string)($validPayment['cf_payment_id']??'');
$pdo->beginTransaction();
try{
 $updated=$pdo->prepare('UPDATE orders SET status="PAID",payment_id=?,expires_at=? WHERE order_id=? AND user_id=? AND status<>"PAID"');
 $updated->execute([$pid,$expiry,$id,$u]);
 if($updated->rowCount()!==1){
  $pdo->rollBack();
  out(200,['status'=>'PAID','premium_unlocked'=>true,'already_processed'=>true]);
 }
 $pdo->prepare('INSERT INTO entitlements(user_id,plan,expires_at,updated_at) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET plan=excluded.plan,expires_at=excluded.expires_at,updated_at=excluded.updated_at')->execute([$u,$o['plan'],$expiry,gmdate('c')]);
 $pdo->commit();
}catch(Throwable $e){
 if($pdo->inTransaction())$pdo->rollBack();
 out(500,['error'=>'entitlement_update_failed']);
}
out(200,['status'=>'PAID','premium_unlocked'=>true,'plan'=>$o['plan'],'expires_at'=>$expiry]);
