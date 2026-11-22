<?php
require __DIR__.'/common.php';
method('GET');
uid(); // Require a verified Firebase user for plan catalogue access.
$plans=[];
foreach(($config['plans']??[]) as $key=>$plan){
  $days=$plan['days']??null;
  $plans[]=[
    'id'=>$key,
    'name'=>ucfirst($key),
    'amount'=>(float)$plan['amount',
    'currency'=>(string)$plan['currency'],
    'description'=>$days===null?'Pay once, keep Premium forever.':'Premium access for '.$days.' days.'
  ];
}
out(200,['plans'=>$plans]);
