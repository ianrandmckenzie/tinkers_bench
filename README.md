# Tinkers' Bench: High-Octane Hytale

_Leave the walking to the mobs._

Tinkers' Bench is a premium server-side vehicle system that brings high-speed, functional motorcycles to the world of Orbis. Whether you're a lone explorer or part of a massive server, Tinkers' Bench adds a layer of mechanical wonder and fast-paced traversal to your gameplay.

## Key Features

*   Rideable Masterpieces: Experience smooth, custom physics and varied movement states designed to feel responsive and weighty.
*   The Key to the Road: Use the Motorcycle Key to summon your vehicle instantly. To store your vehicle and get your keys back, smash up your motorcycle and they will drop! This makes the stakes higher on PvP servers — protect your ride or someone will take it!
*   Immersive Audio: Features a dynamic engine sound system that reacts to your speed. From a low idle growl to a high-speed roar.
*   Server-Side Magic: Since this is a server-side mod, players don’t need to download extra files to start riding!

## How to Get Started

1.  Obtain a **Motorcycle Key** by crafting it in the regular Workbench.
2.  **Left-Click** on a block to summon your motorcycle.
3.  **Interact (Default: Press the F Key)** with the motorcycle to mount it.
4.  Use standard movement keys to drive.
5.  **Interact** with the key again to unsummon (store) the bike.

## Configuration & Customization

Tinkers' Bench is built for server owners. Upon the first launch, a tinkers\_bench.json will be generated in your mods folder. You can fully customize:

Speed & Acceleration: Make your bikes nimble scouts or heavy cruisers. Custom Recipes: Adjust the difficulty of crafting to fit your server's economy.

## Example Config:

```
{

  "bikes": {

    "motorcycle": {

      "craftable": true,

      "power": {

        "baseSpeed": 15.0,

        "acceleration": 0.5,

        "forwardSprintSpeedMultiplier": 2.5

      },

      "recipe": {

        "ingredients": {

          "Ingredient_Bar_Iron": 10,

          "Ingredient_Bar_Thorium": 12,

          "Ingredient_Leather_Medium": 2,

          "Ingredient_Tree_Sap": 8,

          "Ingredient_Bar_Gold": 2

        }

      }

    },

    "steambike": {

      "craftable": true,

      "power": {

        "baseSpeed": 15.0,

        "acceleration": 0.5,

        "forwardSprintSpeedMultiplier": 2.5

      },

      "recipe": {

        "ingredients": {

          "Ingredient_Bar_Iron": 10,

          "Ingredient_Bar_Copper": 14,

          "Ingredient_Leather_Medium": 2,

          "Block_Log_Oak": 8,

          "Ingredient_Bar_Thorium": 2,

          "Ingredient_Bar_Gold": 2

        }

      }

    },

    "voidchariot": {

      "craftable": true,

      "power": {

        "baseSpeed": 15.0,

        "acceleration": 0.5,

        "forwardSprintSpeedMultiplier": 2.5

      },

      "recipe": {

        "ingredients": {

          "Ingredient_Bar_Iron": 10,

          "Ingredient_Tree_Sap": 8,

          "Ingredient_Bar_Silver": 2,

          "Ingredient_Leather_Heavy": 2,

          "Ingredient_Bar_Adamantite": 2,

          "Ingredient_Void_Essence": 50,

          "Ingredient_Void_Heart": 1

        }

      }

    }

  }

}
```

## Installation

1.  Download the asset pack.
2.  Place the `tinkers_bench` folder into your Hytale `mods` or `assets` directory.
3.  Restart your Hytale client/server.

## Feedback

1.  Issues with translation or want to contribute your native tongue? Please contact Ian, `hey@relentlesscurious.com`
2.  Love this pack and want more like it? Why not buy from the store or request a commission? Visit [relentlessCurious.com](https://www.relentlesscurious.com/)
3.  Issues with the asset pack itself? Please visit [the support page](https://www.relentlesscurious.com/faq)

## License

This project is licensed under the MIT License. Feel free to use it in modpacks!
