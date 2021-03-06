# RAFT
## Реализация консенсус-алгоритма RAFT для распределенного K-V хранилища 

1. [Описание системы](#desc)
2. [Get started](#get-started)
3. [Примеры](#examples)


<a name="desc"></a>
## Описание системы. 

![alt text](https://github.com/pleshakoff/raft/blob/master/RAFT.png?raw=true"")

Распределенная CP система для хранения данных в формата KEY-VALUE.
Для репликации данных и поддержания консистентонсти используется консенсус-алгоритм RAFT.
Реализация RAFT написана на Java+Spring Boot.
В текущем документе сам алгоритм не описывается, о нем можно почитать в спецификации https://raft.github.io/raft.pdf
 
Система состоит из двух модулей, для двух типов нод: клиент и сервер. 
Можно развернуть неограниченное количество инстансов как сервера так и клиента.
В текущей конфигурации настроены 3 ноды (для простоты эмуляции нешататных ситуаций) и 1 клиент,  


### Сервер

https://github.com/pleshakoff/raft/tree/master/server

Доступны три ноды. Идентификаторы нод: 1,2,3.

С API можно работать через swagger(подробенее в разделе [API](#api))

#### Описание 

Серверная нода может работать в трех состояниях:   
* Follower. Принимет запросы на чтение от клиента. Принимает heartbeat от лидера  
* Candidate. Принимает запросы на чтение от клиента. Рассылает vote запросы другим нодам 
* Leader. Принимает запросы на чтение и на запись. Рассылает heartbeat запросы другим нодам. 
Рассылает append запросы c данными другим нодам.

Каждая серверная нода обеспечивает доступ к хранилищу лога операций, 
в котором последовательно фиксируются операции по изменению данных. 
  
Также каждая серверная нода имеет доступ к БД в которой хранятся непосредственно данные. 

БД и лог у каждой ноды свои отдельные. 

В текущей реализации используются embedded in-memory решения как для лога, так и для БД.
(конкурентные List и Map) 
В случае необходимости можно просто имплементировать соответствующий интерфейс для поддержки иных типов хранилищ.

Данные лога(операции) реплицируются лидером остальным нодам. После подтверждения получения операции большинством нод, 
операция применяюся state machine и данные попадают в БД. 
После этого, факт того что операция применена, отправляется другим нодам и они применяют её на своей БД. 

Сереверная нода обеспечивает обмен данными с другими нодами поддерживаются два типа запросов: 
* vote при проведении раунда голосования 
* append он же heartbeat(если без данных) для репликации данных лога фоловерам и для предотвращения старта нового раунда голосования. 

На сервере запущены два вида таймеров: 
* vote. Для запуска раунда голосовании. Сбрасывается при получении heartbeat от сервера. 
Настраивается отдельно для каждого сервера. В текущей конфигурации (5 секунд, 7 секунд, 9 секунд)     
* heartbeat. Для отправки append запроса фоловерам. В текущией конфигурации таймаут 2 секунды.

Если сервер не получает heartbeat и таймер голосования истек, он становится кандидатом и 
инициирует выборы, повышает номер раунда голосования и рассылает vote запросы другим нодам.
Если нода соберет большинство голосов, то она становится лидером и начинает рассылать heartbeat.
 
Для того чтобы можно было эмулировать отключения нод без опускания контейнеров, есть возможность через API
останавливать ноды. После остановки нода молчит, она недоступна для других нод и для записи клиентом.  
Но есть возможность получить содержимое лога и БД, что очень удобно для исследований поведения кластера 
в нешататной ситуации. Так же есть black door для вставки данных в лог, что полезно для эмуляции ситуации
когда лидер не знает что он отрезан от кластера и продолжает принимать данные. Подробнее в    
[примерах](#examples)  

      
<a name="api"></a>            
#### API 

Нода #1:  http://localhost:8081/api/v1/swagger-ui.html

Нода #2:  http://localhost:8082/api/v1/swagger-ui.html

Нода #3:  http://localhost:8083/api/v1/swagger-ui.html
   
В API доступны следующее группы методов 

* Context. Получение метаданных ноды. Остановка/запуск ноды 
* Log. CRUD для лога.   
* Storage. Чтение данных из БД. 
* Replication. Эндпоинт для append/heartbeat запросов 
* Election. Эндпоинт для vote запросов

#### Реализация 


Пакеты:

* [node](https://github.com/pleshakoff/raft/tree/master/server/src/main/java/com/raft/server/node). 
Метаданные узла. Раунд голосования, индекс последней примененной операции, данные нодов соседей  и т.д.    
* [election](https://github.com/pleshakoff/raft/tree/master/server/src/main/java/com/raft/server/election). 
Таймер начала выборов. Сервис для отправки и обработки vote реквеста   
* [replication](https://github.com/pleshakoff/raft/tree/master/server/src/main/java/com/raft/server/replication). 
Таймер heeartbeat. Сервис для отправки и обработки append реквеста.   
* [operations](https://github.com/pleshakoff/raft/tree/master/server/src/main/java/com/raft/server/operations). 
Интерфейс для доступа к  логу операций. Его in memory реализация. Сервис для операций с логом.     
* [storage](https://github.com/pleshakoff/raft/tree/master/server/src/main/java/com/raft/server/storage). 
Интерфейс для доступа к БД. Его in memory реализация. Сервис для операций с БД. 
* [context](https://github.com/pleshakoff/raft/tree/master/server/src/main/java/com/raft/server/context). 
Декоратор для удобного доступа к метаданным узла.  

  
### Клиент

https://github.com/pleshakoff/raft/tree/master/client

В текущей конфигурации запускается в единственном экземпляре. 
С API можно работать через swagger(подробенее в разделе [API](#apiclient))

#### Описание 

Отправляет запросы серверу. 
Может собрать метаданные со всего кластера и показать доступные ноды и их состояния 

При запросе на запись ищет лидера и переправляет запрос ему.
Читать можно с лобой ноды. 
В текущей реализации клиент не умеет сам решать с какой ноды запросить данные, 
это надо указать в параметре запроса, так сделано специально 
чтобы удобно было исследовать поведение разных нод.

В текущей реализации клиент при запросе на запись данных не дожидается подтверждения большинством нод, 
как того требует спецификация.
Он просто отправляет запрос асинхронно, а результат уже может быть проверен при попытке чтения.        
      
<a name="apiclient"></a>            
#### API 

Клиент:  http://localhost:8080/api/v1/swagger-ui.html
   
В API доступны следующее группы методов 

* Context. Получение метаданных с всего кластера. Остановка/запуск нод. Получения id лидера.  
* Log. Просмотр лога 
* Storage. CRUD для работы с БД.  

#### Реализация 


Пакеты

* [exchange](https://github.com/pleshakoff/raft/tree/master/client/src/main/java/com/raft/client/exchange). 
Сервис для получения метаданных серверных нод     

Все остальное это просто редиректы к ендпоинтам серверных нод для чтения и записи данных.  

    


<a name="get-started"></a>
## Get started 

В корне репозитория лежит [docker-compose.yml](https://github.com/pleshakoff/raft/blob/master/docker-compose.yml)
его надо запустить, поднимются три серверных ноды и клиент.

` docker-compose up`
 
После запуска через 5 секунд ноды выберут лидера и кластер будет готов к работе. 

##### Swagger 

Нода #1:  http://localhost:8081/api/v1/swagger-ui.html

Нода #2:  http://localhost:8082/api/v1/swagger-ui.html

Нода #3:  http://localhost:8083/api/v1/swagger-ui.html

Клиент:  http://localhost:8080/api/v1/swagger-ui.html

GET запросы можно запускать прямо в браузере. 
Например получить состяние нод можно по ссылке: http://localhost:8080/api/v1/context

##### Vote timeout 

Нода #1:  5 секунд 

Нода #2:  7 секунд

Нода #3:  9 секунд

Таймауты можно перенастроить в docker-compose.yml


##### Логи 

`docker-compose logs -f raft-server-1 `

`docker-compose logs -f raft-server-2`
 
`docker-compose logs -f raft-server-3` 

Если есть желание видеть логи с более подробной информацией то надо в docker-compose.yml
раскомментировать для тэга command параметр с профилем debug 

`--spring.profiles.active=debug`   

Пример: 
`command: --raft.election-timeout=5 --raft.id=1 --server.port=8081 --spring.profiles.active=debug
`

<a name="examples"></a>
## Примеры

Ниже рассмотрен ряд примеров работы с кластером  

Все примеры нужно выполнять через swagger клиентской ноды.
Все то же самое можно сделать обращаясь непосредстваенно к серверным  нодам, но через клиента удобнее.   

Можно переключить лог в debug режим, подробнее см. [Get started](#get-started)

При отключении узла через API, CRUD недоступен. Но операция просмотра лога не блокируется, лог можно посмотреть. 

<a name="election"></a>
### Перевыборы 

Проверяем как поведет себя кластер при потере лидера 

Получаеем ID лидера http://localhost:8080/api/v1/context/leader

Например он равен 1 

Через swagger отключаем лидера от кластера.
   
**POST** http://localhost:8080/api/v1/context/stop?peerId=1 

Получаем данные всего кластера http://localhost:8080/api/v1/context

Видим что новый лидер выбран, а старый лидер по прежнему лидер, но он отключен (active: false)

Отключаем следующего лидера **POST** http://localhost:8080/api/v1/context/stop?peerId=2  

При поптке получить теперь ID лидера терпим неудачу, в кластере нет кворума http://localhost:8080/api/v1/context/leader 

Подключаем узлы, смотрим кто победил. 

**POST** http://localhost:8080/api/v1/context/start?peerId=1

**POST** http://localhost:8080/api/v1/context/start?peerId=2

<a name="normal"></a>
### Штатная репликация  

Проверяем репликацию  

Вставляем,удаляем,редактируем данные через ендопоинты группы storage

Например
**POST** "http://localhost:8080/api/v1/storage 

`{
  "key": 5,
  "val": "test data"
}
`

Читаем данные из БД из разных узлов. 
Например для второго: http://localhost:8080/api/v1/storage?peerId=2 
 
### Отстающие узлы  

Добавляем данные как описано в  пункте [штатная репликация](#normal)

Потом отключаем узлы от кластера как описано в [перевыборы](#election) 
 
Добавляем данные, включаем/отключаем узлы. Желательно в разном порядке. 

Проверяем что все узлы синхронизировались.

http://localhost:8080/api/v1/storage?peerId=1
     
http://localhost:8080/api/v1/storage?peerId=2
     
http://localhost:8080/api/v1/storage?peerId=3     


### Конфликт лидеров 

Эмулируем ситуацию когда лидер отключился от кластера и по прежнему считает что он лидер и продолжает принимать данные.
В это время кластер выбрал нового лидера и тоже продолжает принимать данные. 

Сначала надо отключить лидера и добавть данные в его лог.  

Отключаем текущего лидера как описано в [перевыборы](#election). 
Через клиентскую ноду данные добавить в отключенную ноду нельзя. 
Поэтому подключаемся напрямую к серверной ноде которую мы отключили.   
Например если лидером был 1 то http://localhost:8081/api/v1/swagger-ui.html (id = последняя цифра порта) 

Используем специальный метод, который позволяет добавить данные в лог отключенной ноды 

**POST** http://localhost:8081/api/v1/log/sneaky
{
  "key": 1000,
  "val": "BAD DATA"
}   

Обращаемся по прежнему к серверной ноде напрямую 

Убеждаемся что данные попали в лог http://localhost:8081/api/v1/log

Проверяем что эти данные не попали из лога в БД для данной ноды, потому что нет кворума http://localhost:8081/api/v1/storage

Теперь добавляем данные в кластер через доступного лидера. На этот раз штатно через клиентскую ноду, как описано в пункте [штатная репликация](#normal)

Таким образом мы получили два лидера оба с данными, у одного данные реплицированы и подтверждены большинством, 
у второго нет. 

Поднимаем отключенного лидера проверяем его лог и хранилище, там должны быть правильные данные из кластера
 
http://localhost:8080/api/v1/log?peerId=1

http://localhost:8080/api/v1/storage?peerId=1


 




  
 
 
