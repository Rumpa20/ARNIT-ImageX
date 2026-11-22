<?php
require __DIR__.'/common.php';method('GET');$u=uid();$q=db()->prepare('SELECT plan,expires_at FROM entitlements WHERE user_id=?');$q->execute([$u]);$e=$q->fetch(PDO::FETCH_ASSOC);if(!$e)out(200,['premium'=>false]);if($e['expires_at']&&strtotime($e['expires_at'])<=time())out(200,['premium'=>false,'expired'=>true]);out(200,['premium'=>true,'plan'=>$e['plan'],'expires_at'=>$e['expires_at']]);
