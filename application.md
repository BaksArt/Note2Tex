# Артем Щелков - "Note2Tex"
### Группа: 10 - И - 4
### Электронная почта: arshchelkov@gmail.com
### Tg: @BaksArt


**[ НАЗВАНИЕ ПРОЕКТА ]**

“Note2Tex”

**[ ПРОБЛЕМНОЕ ПОЛЕ ]**

С ростом объема научных исследований и образовательных практик, основанных на использовании рукописных текстов, возникает необходимость в разработке эффективных инструментов для их преобразования и форматирования. При этом традиционные методы OCR (оптического распознавания символов) часто не справляются с распознаванием рукописного текста, особенно учитывая многообразие почерков, стилей написания и языков. К сервисам, которые могут адекватно преобразовать рукописный текст в LaTeX, предъявляются следующие требования: 
1) высокая точность распознавания рукописных символов и математической нотации
2) возможность обработки текста, содержащего формулы и специальные символы
3) удобный пользовательский интерфейс для быстрой и простой работы с приложением.
  
Тем не менее, многие из существующих решений имеют серьезные ограничения: 
1) низкая точность распознавания рукописного ввода, что приводит к многочисленным ошибкам и необходимости дополнительного редактирования
2) отсутствие возможности превращения распознанного текста непосредственно в формат LaTeX, что затрудняет дальнейшую корректировку документа
3) отсутствие встроенного редактора LaTex 

Заявляемый проект No2Tex позволит решить эти проблемы и предоставит пользователям надежный инструмент для перевода рукописного текста в LaTeX и PDF форматы, сохраняя при этом высокую точность распознавания и удобство использования.

**[ ЗАКАЗЧИК / ПОТЕНЦИАЛЬНАЯ АУДИТОРИЯ ]**

Программный продукт No2Tex создан для широкой аудитории, заинтересованной в преобразовании рукописного текста в LaTeX и PDF форматы, однако можно выделить несколько ключевых групп пользователей, для которых разработаны специфические функции. 
К выделяемым группам относятся:

* Студенты и исследователи в области точных наук, включая математику, физику и химию
* Преподаватели математических дисциплин, для которых важно быстрое создание учебных материалов
* Широкая аудитория студентов и школьников, желающих улучшить качество своих учебных работ(доклады, рефераты, домашние задания)

**[ АППАРАТНЫЕ / ПРОГРАММНЫЕ ТРЕБОВАНИЯ ]** 

Продукт разрабатывается под систему Android со следующими требованиями:

* Версия Android: Nougat (Android 7) и выше
* Дисковое пространство: не менее 10 ГБ
* ОЗУ: не менее 4 ГБ

**[ ФУНКЦИОНАЛЬНЫЕ ТРЕБОВАНИЯ ]**

Программный продукт будет предоставлять следующие возможности:
* Преобразование рукописного текста в формат Latex с помощью технологий ИИ
* Генерация PDF документов на основе преобразованного Latex файла
* Высокоточное распознавание математических формул и символов, превышающее возможности стандартного OCR
* Возможность загрузки изображений с рукописным текстом, а также захват фотографий с камеры для анализа
* Возможность загрузки изображений со сторонних приложений
* Поддержка английского и русского языков для распознавания текста и формул
* Удобный интерфейс для просмотра и редактирования распознанного текста перед экспортом
* Возможность сравнения оригинала с распознанным текстом для проверки точности
* Экспорт полученных документов в разные форматы (как минимум – .pdf, .tex, .docx)
* Возможность поделиться полученным документом(отправить по электронной почте, в мессенджеры, загрузить в облачное зранилище)
* Возможность создания аккаунтов для хранения истории преобразований

**[ ПОХОЖИЕ / АНАЛОГИЧНЫЕ ПРОДУКТЫ ]**

Анализ 3 программных продуктов, которые максимально приближены к заданному функционалу, показал, что:

* Продукт – 1: не распознает русский текст, количество распознаваний сильно ограничено(10 распознаваний на 1 месяц)
*	Продукт – 2: не распознает русский текст, индексы переменных, матрицы, не видит некоторые символы, ошибается в английских словах. Отсутствие возможности редактировать документ после преобразования. Невозможно экспортировать в форматы(.pdf, .docx)
* Продукт – 3: не распознает некоторые символы, индексы. Текст, написанный на разных строчках объединяется в одну строчку. Также отсутствует возможность редактирования и экспорта документа в другие форматы.

**[ ИНСТРУМЕНТЫ РАЗРАБОТКИ, ИНФОРМАЦИЯ О БД ]**

*	Java / Android Studio – для разработки Android-версии
*	Java / Intellij Idea – для разработки бэкенда
*	Python / VS code - для разработки модели ИИ

**[ ЭТАПЫ РАЗРАБОТКИ ]**

*	Разработка пользовательских сценариев
*	Проектирование интерфейса
*	Создание модели ИИ для распознавания рукописного текста и преобразования в Latex
*	Создание модуля загрузки изображений в приложение
*	Создание редактора для изменения и просмотра преобразованных документов
*	Создание модуля экспорта документов в форматы .pdf, .docx
*	Создание системы аутентификации
*	Запуск Android версии продукта
*	Тестирование, отладка
*	Подготовка проекта к защите

**[ ВОЗМОЖНЫЕ РИСКИ ]**

*	Невозможность достаточно качественно обучить модель, для распознавания английского, русского языков и математических символов
*	Невозможность спроектировать понятный и удобный пользовательский интерфейс 
*	Невозможность предоставить то количество уникальных функции, или улучшенное качество, чтобы привлечь пользователей