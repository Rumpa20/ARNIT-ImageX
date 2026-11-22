<?php
// Copy to config.php on server; never commit credentials.
return [
 'app_id'=>getenv('CASHFREE_APP_ID') ?: '',
 'secret'=>getenv('CASHFREE_SECRET_KEY') ?: '',
 'api_version'=>'2025-01-01',
 'environment'=>getenv('CASHFREE_ENV') ?: 'sandbox',
 'webhook_secret'=>getenv('CASHFREE_WEBHOOK_SECRET') ?: '',
 'plans'=>[
  'monthly'=>['amount'=>99.00,'currency'=>'INR','days'=>30],
  'yearly'=>['amount'=>799.00,'currency'=>'INR','days'=>365],
  'lifetime'=>['amount'=>1499.00,'currency'=>'INR','days'=>null],
 ],
 'return_url'=>getenv('IMAGEX_RETURN_URL') ?: 'https://example.invalid/payment-return',
 'db_path'=>__DIR__.'/private/payment.sqlite',
];
