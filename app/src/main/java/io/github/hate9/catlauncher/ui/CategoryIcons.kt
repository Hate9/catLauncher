/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.hate9.catlauncher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated Material Symbols (Apache 2.0) that suit app categories. Names are stored in the config,
 * so entries must keep their names once released; add new ones at the end of a group.
 */
object CategoryIcons {
    val favorites: ImageVector = Icons.Rounded.Star
    val hidden: ImageVector = Icons.Rounded.VisibilityOff

    val all: List<Pair<String, ImageVector>> = listOf(
        // general
        "home" to Icons.Rounded.Home, "star" to Icons.Rounded.Star, "favorite" to Icons.Rounded.Favorite,
        "apps" to Icons.Rounded.Apps, "grid" to Icons.Rounded.GridView, "widgets" to Icons.Rounded.Widgets,
        "dashboard" to Icons.Rounded.Dashboard, "category" to Icons.Rounded.Category, "label" to Icons.Rounded.Label,
        "bookmark" to Icons.Rounded.Bookmark, "flag" to Icons.Rounded.Flag, "more" to Icons.Rounded.MoreHoriz,
        "settings" to Icons.Rounded.Settings, "build" to Icons.Rounded.Build, "tune" to Icons.Rounded.Tune,
        "extension" to Icons.Rounded.Extension, "inventory" to Icons.Rounded.Inventory2, "archive" to Icons.Rounded.Archive,
        "history" to Icons.Rounded.History, "schedule" to Icons.Rounded.Schedule, "alarm" to Icons.Rounded.Alarm,
        "timer" to Icons.Rounded.Timer, "calendar" to Icons.Rounded.CalendarMonth, "event" to Icons.Rounded.Event,
        "notifications" to Icons.Rounded.Notifications, "search" to Icons.Rounded.Search, "info" to Icons.Rounded.Info,
        // work and productivity
        "work" to Icons.Rounded.Work, "business" to Icons.Rounded.Business, "badge" to Icons.Rounded.Badge,
        "description" to Icons.Rounded.Description, "article" to Icons.Rounded.Article, "note" to Icons.Rounded.EditNote,
        "edit" to Icons.Rounded.Edit, "draw" to Icons.Rounded.Draw, "folder" to Icons.Rounded.Folder,
        "task" to Icons.Rounded.TaskAlt, "checklist" to Icons.Rounded.Checklist, "list" to Icons.Rounded.List,
        "calculate" to Icons.Rounded.Calculate, "functions" to Icons.Rounded.Functions, "print" to Icons.Rounded.Print,
        "mail" to Icons.Rounded.Mail, "send" to Icons.Rounded.Send, "contacts" to Icons.Rounded.Contacts,
        "cloud" to Icons.Rounded.Cloud, "storage" to Icons.Rounded.Storage, "backup" to Icons.Rounded.Backup,
        // communication and social
        "chat" to Icons.Rounded.Chat, "forum" to Icons.Rounded.Forum, "message" to Icons.Rounded.Message,
        "sms" to Icons.Rounded.Sms, "call" to Icons.Rounded.Call, "videocall" to Icons.Rounded.VideoCall,
        "person" to Icons.Rounded.Person, "people" to Icons.Rounded.People, "groups" to Icons.Rounded.Groups,
        "public" to Icons.Rounded.Public, "language" to Icons.Rounded.Language, "translate" to Icons.Rounded.Translate,
        "share" to Icons.Rounded.Share, "rss" to Icons.Rounded.RssFeed, "newspaper" to Icons.Rounded.Newspaper,
        // media
        "music" to Icons.Rounded.MusicNote, "headphones" to Icons.Rounded.Headphones, "podcast" to Icons.Rounded.Podcasts,
        "radio" to Icons.Rounded.Radio, "mic" to Icons.Rounded.Mic, "speaker" to Icons.Rounded.Speaker,
        "movie" to Icons.Rounded.Movie, "tv" to Icons.Rounded.Tv, "video" to Icons.Rounded.Videocam,
        "play" to Icons.Rounded.PlayCircle, "camera" to Icons.Rounded.PhotoCamera, "photo" to Icons.Rounded.Photo,
        "gallery" to Icons.Rounded.Collections, "image" to Icons.Rounded.Image, "brush" to Icons.Rounded.Brush,
        "palette" to Icons.Rounded.Palette, "book" to Icons.Rounded.MenuBook, "library" to Icons.Rounded.LocalLibrary,
        "theater" to Icons.Rounded.TheaterComedy, "piano" to Icons.Rounded.Piano, "subtitles" to Icons.Rounded.Subtitles,
        // games and fun
        "games" to Icons.Rounded.SportsEsports, "casino" to Icons.Rounded.Casino, "toys" to Icons.Rounded.Toys,
        "robot" to Icons.Rounded.SmartToy, "rocket" to Icons.Rounded.RocketLaunch, "trophy" to Icons.Rounded.EmojiEvents,
        "celebration" to Icons.Rounded.Celebration, "sparkle" to Icons.Rounded.AutoAwesome, "diamond" to Icons.Rounded.Diamond,
        "mood" to Icons.Rounded.Mood, "face" to Icons.Rounded.Face, "puzzle" to Icons.Rounded.Extension,
        "sports" to Icons.Rounded.SportsSoccer, "basketball" to Icons.Rounded.SportsBasketball, "chess" to Icons.Rounded.Castle,
        // tools and tech
        "code" to Icons.Rounded.Code, "terminal" to Icons.Rounded.Terminal, "developer" to Icons.Rounded.DeveloperMode,
        "bug" to Icons.Rounded.BugReport, "computer" to Icons.Rounded.Computer, "laptop" to Icons.Rounded.Laptop,
        "phone" to Icons.Rounded.PhoneAndroid, "tablet" to Icons.Rounded.Tablet, "watch" to Icons.Rounded.Watch,
        "keyboard" to Icons.Rounded.Keyboard, "mouse" to Icons.Rounded.Mouse, "memory" to Icons.Rounded.Memory,
        "wifi" to Icons.Rounded.Wifi, "bluetooth" to Icons.Rounded.Bluetooth, "router" to Icons.Rounded.Router,
        "usb" to Icons.Rounded.Usb, "battery" to Icons.Rounded.BatteryFull, "power" to Icons.Rounded.Power,
        "bolt" to Icons.Rounded.Bolt, "lightbulb" to Icons.Rounded.Lightbulb, "science" to Icons.Rounded.Science,
        "biotech" to Icons.Rounded.Biotech, "psychology" to Icons.Rounded.Psychology, "school" to Icons.Rounded.School,
        // security and privacy
        "security" to Icons.Rounded.Security, "shield" to Icons.Rounded.Shield, "lock" to Icons.Rounded.Lock,
        "key" to Icons.Rounded.Key, "vpn" to Icons.Rounded.VpnKey, "password" to Icons.Rounded.Password,
        "verified" to Icons.Rounded.Verified, "block" to Icons.Rounded.Block, "visibility_off" to Icons.Rounded.VisibilityOff,
        // money and shopping
        "money" to Icons.Rounded.AttachMoney, "payments" to Icons.Rounded.Payments, "wallet" to Icons.Rounded.AccountBalanceWallet,
        "bank" to Icons.Rounded.AccountBalance, "card" to Icons.Rounded.CreditCard, "savings" to Icons.Rounded.Savings,
        "chart" to Icons.Rounded.ShowChart, "trending" to Icons.Rounded.TrendingUp, "analytics" to Icons.Rounded.Analytics,
        "cart" to Icons.Rounded.ShoppingCart, "bag" to Icons.Rounded.ShoppingBag, "store" to Icons.Rounded.Storefront,
        "receipt" to Icons.Rounded.Receipt, "sell" to Icons.Rounded.Sell, "gift" to Icons.Rounded.CardGiftcard,
        // travel and places
        "map" to Icons.Rounded.Map, "place" to Icons.Rounded.Place, "explore" to Icons.Rounded.Explore,
        "navigation" to Icons.Rounded.Navigation, "flight" to Icons.Rounded.Flight, "car" to Icons.Rounded.DirectionsCar,
        "bus" to Icons.Rounded.DirectionsBus, "train" to Icons.Rounded.Train, "bike" to Icons.Rounded.DirectionsBike,
        "walk" to Icons.Rounded.DirectionsWalk, "hotel" to Icons.Rounded.Hotel, "luggage" to Icons.Rounded.Luggage,
        "hiking" to Icons.Rounded.Hiking, "sailing" to Icons.Rounded.Sailing, "park" to Icons.Rounded.Park,
        // health and lifestyle
        "fitness" to Icons.Rounded.FitnessCenter, "run" to Icons.Rounded.DirectionsRun, "spa" to Icons.Rounded.Spa,
        "meditation" to Icons.Rounded.SelfImprovement, "hospital" to Icons.Rounded.LocalHospital, "medical" to Icons.Rounded.MedicalServices,
        "medication" to Icons.Rounded.Medication, "health" to Icons.Rounded.HealthAndSafety, "monitor_heart" to Icons.Rounded.MonitorHeart,
        "bed" to Icons.Rounded.Bed, "nightlight" to Icons.Rounded.NightsStay, "sunny" to Icons.Rounded.WbSunny,
        "umbrella" to Icons.Rounded.Umbrella, "snow" to Icons.Rounded.AcUnit, "fire" to Icons.Rounded.LocalFireDepartment,
        "water" to Icons.Rounded.WaterDrop, "pets" to Icons.Rounded.Pets, "eco" to Icons.Rounded.Eco,
        "forest" to Icons.Rounded.Forest, "grass" to Icons.Rounded.Grass, "recycling" to Icons.Rounded.Recycling,
        // food and home
        "restaurant" to Icons.Rounded.Restaurant, "cafe" to Icons.Rounded.LocalCafe, "bar" to Icons.Rounded.LocalBar,
        "fastfood" to Icons.Rounded.Fastfood, "pizza" to Icons.Rounded.LocalPizza, "cake" to Icons.Rounded.Cake,
        "grocery" to Icons.Rounded.LocalGroceryStore, "kitchen" to Icons.Rounded.Kitchen, "chair" to Icons.Rounded.Chair,
        "house" to Icons.Rounded.House, "cleaning" to Icons.Rounded.CleaningServices, "yard" to Icons.Rounded.Yard,
        "lamp" to Icons.Rounded.Light, "thermostat" to Icons.Rounded.Thermostat, "delete" to Icons.Rounded.Delete,
    )

    private val byName: Map<String, ImageVector> = all.toMap()

    operator fun get(name: String?): ImageVector? = name?.let { byName[it] }
}
