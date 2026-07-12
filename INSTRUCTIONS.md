You are taksed to build a simple Android app for tracking calories and macros.


There a hundreds of existing apps. The key differentiator is the UX experience. The main goals here are simple:

1. Usability comes first. We need to think carefully about the main usage flows and make sure they are as frictionless as possible. It should be easy to scan over the main app view for all the information you need. Common actions like copying meals from one day to another should take as few touches as possible.

2. Simplicity is key. This is how we can differentiate ourselves from other apps which have become bloated with feature creep. Every single feature, button and action should be thoughtful. We will track only kcal, protein, carbs, and fats, which will make our app simpler.

3. The app needs to be responsive and snappy. Even a couple hundred milliseconds is noticeable and makes the app feel slow and unresponsive. Performance is vital.

4. The app should be beautiful. The UX should be pretty and consistent across all views using consistent styling. Colour is used to maximize clarity across the app.

Achieving this will require careful planning to ensure that the design maximises usability.


We will be following software engineering best practices to make sure we build a reliable, fast app that is easily extensible and well-tested. Tests are non-negotiable and should come first. We follow SOLID design principles, we build small compostable components that each have clear single responsibilities and that are easy to extend. Use well established patterns like MVVM. Use well established, and frequently and recently updated frameworks and libraries where possible.


There is a lot to do here. Let's work iteratively. Start with setting up a framework with documentation in place of code that needs to still be written. Then we fill in the code gradually and iterate to improve.


Let's start now by documenting a thorough plan.


Here is a list of features we want to build:

1 Simple daily log view

2 Quick add macros

3 USDA food data central search feature

4 Open food facts data

---

5 UK supermarkets scraped data

6 UK cafes and restaurants scraped data

7 Graph view + widgets

8 Meal prep mode

9 Health sync integration and online backup

10 Meal reminders


Let's focus on features 1-4 to build an MVP to start with. I hope we can do it entirely locally to start with, no need for server. But let's see if we hit any issues.


LOG VIEW

The main window for the app.


At the top of the window is a date selection. Current week monday-sunday is selectable via a day/date selection. Each selectable date is accompanied by a bar to show kcal % on that day of current logs. Calendar modal available for choosing to view another week.


Swipe left/right takes you to the previous/next day.


Next is a summary of kcal, protein, carbs, and fats currently logged compared to daily goals for the selected date. Some graphical view as well as absolute and percentage numbers. Access to SETTINGS VIEW should be here.


Following this is the actual log. It lists the food items logged under configurable sections (e.g. "breakfast", "lunch", "dinner" or hourly etc.). Each food item also lists kcal, protein, carbs and fats. Each section also lists the same metrics aggregated. At the bottom of each section are buttons to add via search, label scan, barcode scan, or quick add.


Touch and hold an item allows you to drag and drop it to a different section.


Touch and hold a section or touch and hold an item without moving turns into a selection mode. The selected items are highlighted. Touching an item adds/removes the item to the selection. Touching a section adds/removes the entire section to the selection. Three options appear pinned to the bottom of the screen (no need for add buttons in selection mode). These are copy to, move to, and delete. When touching copy to/move to, three/four new options appear at the bottom of the screen: yesterday, today, tomorrow. If the user is currently viewing or navigates the log for a different day than those 3, the selected day is also shown. Copying/moving in this way maintains the sections (if possible). The buttons to add food at the end of each section also switch in selection mode, to copy here, move here.


Pinned to the bottom of the screen should be buttons to add foods, via search, label scan, barcode scan, or quick add. These add to the current section (based on current time) for the currently selected date.


ADD VIEW

At the very top of the add view is the selected date and section to add to. Touching on either of these allows the user to change the selection.


Back always take us back to the LOG VIEW for the selected date.


Below this is the add mode selection: search, label scan, barcode scan, or quick add.


Below this is the actual interface to add foods.


SEARCH

When search is empty, we show recommendations based on historical logs. Use simple heuristics to favour 1: recency in this section, 2: frequency in this section, 3: recency overall, 4: frequency overall.


Otherwise we do a prefix search against data available from our data sources (at first, this should be USDA data and open food facts). Let's work out how to do the search during the planning. I would hope that it is something we can do locally. I.e. we download all the food data, store just brand, name, EAN, portion size, portion size label (think "one slice", "75g cooked", "12 sweets"), kcal/100g, g protein/100g, g carbs/100g, g fats/100g. We may need to do something smart like put brands and names into prefix tries depending on how large we expect the data to be? There are probably libraries for this that would work better than doing something ourselves? Let's investigate this. We should also make sure we rank items that have been logged before higher than items that haven't been logged before.


For each result, show the brand, name, portion size label and portion size in g, and kcal, protein, carbs and fats for the portion size. The portion size should be the most recently logged portion size for previously logged foods, otherwise the default portion size if available from the data, otherwise 100g. There should be a small add button which adds that portion size directly to the log without existing this screen. Touching anywhere else on the entry brings up a PORTION SIZE VIEW to select a specific portion size. This view shows the kcal and macros of the currently selected food in a pie chart view. Below is the actual portion size selection. This view always starts with the default portion size, not the last logged portion size. Here the user can replace this with an exact portion size in g. There are also buttons to easily add a fraction or multiple of the default portion size (say, 1/4, 1/3, 1/2, 1, 2, 3, 4), or type in a custom multiple of the default portion size. Adding an item clears the search terms but takes us back to SEARCH.


LABEL SCAN

Camera view to scan a UK style nutrition label. This bit needs some planning to work out the correct technical approach. We definitely will need some library here. I want this to be done on device so it can be fast and free. I am thinking something like simple OCR + pattern matching but we will need to think through this. Needs to extract: brand, name, portion size in g, portion size label, kcal/100g, g protein/100g, g carbs/100g, g fats/100g. We should always do simple validation check like make sure that macros add up to the correct kcals for 100g. Calculate portion size in g such that portion size in g/100 * kcals and macros for 100g give the portion size values on the nutrition label. Kcals for a portion size should pretty much never be more than, say, 2000kcal. Let's add a bunch of checks like this to help avoid mistakes.


BARCODE SCAN

Camera view to scan a barcode. Upon scanning an isbn that is in our database, go to PORTION SIZE VIEW. If not found, go to QUICK ADD, remembering the EAN. After adding, go back to BARCODE SCAN.


QUICK ADD

Basic form entry to add a food. Keep it as simple as possible.


Barcode scan for EAN (optional)


Name (required)

Brand (optional)


Portion size label (optional)

Portion size in g (optional)


Kcal 100g/portion (at least one required)

Protein 100g/portion (at least one required)

Carbs 100g/portion (at least one required)

Fat 100g/portion (at least one required)


Apply constraints and automatically fill in data as soon as you can.

E.g., if the user provides portion size, calculate the 100g/portion value automatically.

E.g., if the user provides a 100g and a portion nutrition value, then calculate the portion size and use that for further calculation.

E.g., make sure the macros add up to kcals.


Add to a local database of added foods. Take to PORTION SIZE VIEW. After adding, go back to QUICK ADD.


SETTINGS VIEW

Just three settings to start with.


First is the daily goals for kcals and macros. The user can change the macro goals in g. Percent of kcal from each macro and the overall kcal goal is calculated automatically from this.


Next is the log sections. These have a name, a time. The default is:

Breakfast 08:00

Morning snack 11:00

Lunch 12:30

Afternoon snack 14:00

Dinner 18:00

Evening snack 22:00

When adding a food without selecting a specific section, default to the section closest to the current time.


Finally, each of the macro goals can be distributed across the sections. This is behind a toggle. Try to make it as easy as possible to configure and obviously make sure the section goals add to 100% for each macro.


OK, so now let's start by fleshing this out into a full plan. Let's think about the overall architecture and code structure. Choose a stack that is well supported and easy to use and iterate quickly with. Work out what data we need to store, what schema to use, where to store it and in which format to maximize usability, scalability and performance. Work out what models, views, components etc. are required and how they will all fit together. Work out a plan for a proper testing framework.