# wachter

Сервис предназначен для авторизации и проксирования вызовов от [control-center](https://github.com/valitydev/control-center).

## Описание работы сервиса

1. Wachter получает от [control-center](https://github.com/valitydev/control-center) запрос на проведение операции, 
содержащий токен и имя сервиса, в который необходимо спроксировать запрос. Имя сервиса получает из header "Service".
2. Из сообщения запроса wachter получает 
[имя метода](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/service/MethodNameReaderService.java)
3. В [KeycloakService](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/service/KeycloakService.java) 
wachter получает partyId и AccessToken. 
4. По имени сервиса из header wachter
[маппит](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/mapper/ServiceMapper.java)
url, на который необходимо спроксировать запрос.
5. Далее сервис проверяет возможность [авторизации](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/security/AccessService.java) 
пользователя в [bouncer](https://github.com/valitydev/bouncer),
формируя [контекст](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/security/BouncerContextFactory.java)
на основе [данных](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/security/AccessData.java),
полученных из запроса. Контекст формируется с учетом фрагмента [wachter](https://github.com/valitydev/bouncer-proto/blob/master/proto/context_v1.thrift#L49)
6. Если доступ разрешен, сервис [отправляет запрос](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/client/WachterClient.java) 
на ранее смаппленный урл.
7. Полученный ответ [возвращает](https://github.com/valitydev/wachter/blob/master/src/main/java/dev/vality/wachter/controller/WachterController.java) control-center.

Схема работы сервиса:

![diagram-wachter](doc/diagram-wachter.svg)

