

// curl 'https://data.muni.org/api/views/a9a7-y93v/rows.json?accessType=DOWNLOAD' | jq '.data | .[] | .[8]' | shuf | head -200
const petNames = [
    "MARIE", "TELLY", "SLATER", "FINNIGAN", "KANU", "BILLY", "JACKIE", "ASUNA", "HUSTLER", "68 WHISKEY", "FISHBONES",
    "SCHNITZEL", "DROOPY", "DANIKIS", "BLACK JACK", "BUBBLEZ", "ZORBA", "RAYNIE MAE", "Q", "ARTEMIS", "SPEEDY", "KRAZEE",
    "BADGER", "AVERY", "SINA", "CASSI", "DANIELLE", "BRUCE WAYNE", "J.C.", "ZUNI", "KNOX", "D.O.G.", "HERSHEY", "YARROW",
    "DAISEY", "GODIVA", "PHINNEAS", "ROMAC", "WILLIS", "LANI", "SQUALL", "LOU", "PEPE LE PEAU", "CHICKADEE", "JIMMY",
    "APPLE", "ROXIE", "DEUCE", "GRIFFEN", "VALENTINO", "RASCAL", "DARCIE", "SAMMI", "B.B.", "FINN", "SHILOH", "ROCKY BLUE",
    "CHUY", "MOANA", "LILA BLUEBEL", "PEASLEY", "LAIKA", "INDY", "MAXI", "MELISSA", "WALDO V", "SHASHA", "TAITTI",
    "KOTA", "LENNY", "JEZABELLE", "GRUMBLE", "GINNY", "ROSIE", "CODY", "NILLA", "BUTCH", "AJAX", "BELLADONA", "CASH",
    "LUKKA", "NERO", "SHERMAN", "LOKI", "BELL A DONNA", "MCGYVER", "MAXIMILLIAN", "MOCA", "LADY", "TATIA", "WARD",
    "PESKY", "DELILAH", "SYRAH", "FARRAH", "SHOOTER", "MULDER", "GOOBER", "IRA", "HOOCH", "DENEKI 4", "MUSHU", "SHELDON", "TUBBY",
    "BITZER", "MARKER", "KRASH", "SPIRIT", "HERA", "LUCY LU", "BLUE", "STORMY", "NAVI", "MARLI", "CHOCHO", "DYLAN",
    "TOBY", "BRYCE", "TIGGER", "MARY ANN", "RIKU", "PHOENIX", "MEKA", "CUDDY", "SABRINA", "DAYTON", "DAKODA", "OGUN",
    "SOLAR", "RAJ", "DIESEL", "RIPLEY 2", "CARLIE", "ROSEY", "SWISS", "KILLER", "KENLEY", "FEENA", "PIGLET",
    "KEYONA", "CHEWIE", "TEINE", "CORWIN", "FLOPPER", "ADELAIDE", "HUNNY", "GEDEON", "BEE", "TALACHULITNA", "PATTON", "KLOEE",
    "TRANGO", "IZZI", "PEPPERMINT", "TILLAMOOK", "QI", "KEESTER BOB", "KAINANI", "ELLERA", "RACHEL", "DUKE", "MAZEL", "PRINCE FREDR",
    "KATE", "GANDHI", "DREAM", "NINA INDIANA", "JD", "ZIESA", "SAVAGE", "WRANGLER", "MOZZIE", "OSA", "BRODY JOE", "PI", "TOBO JET",
    "KHERNAL", "HOPS", "CHEWEY", "KODIAK", "BARTLETT", "MACKENZIE", "RILEY", "GORDY", "SARGENT", "DIVER", "ATTU", "KETEL",
    "MIKKI", "FOSBURY", "BESSA", "DESHKA", "WINCHESTER", "ZOEY MARIE", "AUSTIN", "DJANGO", "KINGSTON", "BLAZE", "SABBATH",
    "SPRUCE",
];

// curl https://raw.githubusercontent.com/aruljohn/popular-baby-names/master/2017/boy_names_2017.json | jq '.names | .[]' | shuf | head -100 | sed -e "s/$/,/"
const firstNames = [
    "Finley", "Jade", "Kylee", "Kensley", "Kira", "Brenna", "Braelyn", "Marley", "Phoenix", "Raegan", "Phoebe", "Kaya",
    "Khloe", "Angelique", "Kiana", "Magdalena", "Allison", "Anahi", "Elisabeth", "Juliet", "Miriam", "Elliott",
    "Josie", "Brenda", "Hadassah", "Kathryn", "Nadia", "Cataleya", "Lilah", "Yamileth", "Alayna", "Rowan", "Andi",
    "Ember", "Gwendolyn", "Jacqueline", "Cecelia", "Kimora", "Lindsey", "Opal", "Lyric", "Dakota", "Jennifer", "Dorothy", "Nia",
    "Gabriela", "Mariana", "Haley", "Maliah", "Kelly", "Sloane", "Danica", "Stevie", "Erica", "Jaycee", "Aliya", "Marissa", "Elise",
    "Mila", "Oakley", "Adelaide", "Maci", "Dayana", "Aniyah", "Estrella", "Scarlette", "Joy", "Raina", "Sarai", "Luella", "Averi",
    "Rebecca", "Eloise", "June", "Hadleigh", "Lena", "Natasha", "Brynn", "Alexis", "Summer", "Clarissa", "Katelyn", "Alexa", "Louise",
    "Marianna", "Elsa", "Kehlani", "Tessa", "Katie", "Paisley", "Jasmine", "Brooklyn", "Aubree", "Averie", "Quinn", "Chloe",
    "Paula", "Kassidy", "Alessia", "Juliette", "Aydin", "Lucian", "Bronson", "Ryder", "Felix", "Major", "Darius", "Tony", "Junior",
    "William", "Johnny", "Patrick", "Raiden", "Nikolas", "Phillip", "Nickolas", "Markus", "Vicente", "Kody", "Rory", "Julian", "Corbin",
    "Benjamin", "Dexter", "Emilio", "Jesse", "Romeo", "August", "Gerald", "Gary", "Terrence", "Troy", "Axl", "Ronnie",
    "Eugene", "Foster", "Matthias", "Maurice", "Wells", "Kyler", "Leland", "Elian", "Shaun", "Callan", "Samson", "Hector", "Curtis",
    "Allan", "Jaxton", "Edgar", "Jonah", "Raul", "Lawrence", "Jude", "Jamison", "Kaysen", "Colten", "Alan", "Tripp", "Aarav",
    "Xander", "Landen", "Caden", "Garrett", "Marcelo", "Sean", "Christian", "Reed", "Vaughn", "Kylen", "Ace",
    "Kole", "Kristian", "Francis", "Brysen", "Josiah", "Riley", "Brantley", "Colby", "Sonny", "Braydon", "Conrad",
    "Michael", "Trent", "Andres", "Billy", "Luka", "Stanley", "Alden", "Victor", "Axton", "Jamari", "Henry", "Jadiel", "Elliott",
    "Kristopher", "Kyrie", "Ruben", "Ahmed", "Theodore"
];

// $ curl https://raw.githubusercontent.com/rossgoodwin/american-names/master/surnames.json | jq | shuf | tail -25
const surnames = [
 "Coomes", "Kasputis", "Eing", "Budro", "Paszkiewicz", "Reichwald", "Mennona", "Esplin", "Trute", "Endlich",
  "Kaman", "Coody", "Urish", "Styes", "Balles", "Semanek", "Tes", "Mediano", "Clave", "Beliard", "Christianson",
  "Doy", "Bozman", "Waligura", "Templeman", "Gershenson", "Eckberg", "Harader", "Baurer", "Villao", "Decius",
  "Marquardt", "Smaha", "Grzych", "Getto", "Wilberger", "Fleites", "Spoerl", "Oliger", "Gramza", "Prillaman",
  "Beinlich", "Marzella", "Bota", "Arguilez", "Piotti", "Karri", "Spiropoulos", "Gambhir", "Franchak"
];

// source https://github.com/baliw/words/blob/master/adjectives.json
const adj = ["Ablaze", "Abrupt", "Accomplished", "Active", "Adored", "Adulated", "Adventurous", "Affectionate", "Amused", "Amusing",
    "Animal-like", "Antique", "Appreciated", "Archaic", "Ardent", "Arrogant", "Astonished", "Audacious", "Authoritative",
    "Awestruck", "Beaming", "Bewildered", "Bewitching", "Blissful", "Boisterous", "Booming", "Bouncy", "Breathtaking",
    "Bright", "Brilliant", "Bubbling", "Calm", "Calming", "Capricious", "Celestial", "Charming", "Cheerful", "Cherished",
    "Chiaroscuro", "Chilled", "Comical", "Commanding", "Companionable", "Confident", "Contentment", "Courage", "Crazy",
    "Creepy", "Dancing", "Dazzling", "Delicate", "Delightful", "Demented", "Desirable", "Determined", "Devoted", "Dominant",
    "Dramatic", "Drawn out", "Dripping", "Dumbstruck", "Ebullient", "Elated", "Elegant", "Enchanted", "Energetic",
    "Enthusiastic", "Ethereal", "Exaggerated", "Exalted", "Expectant", "Expressive", "Exuberant", "Faint", "Fantastical",
    "Favorable", "Febrile", "Feral", "Feverish", "Fiery", "Floating", "Flying", "Folksy", "Fond", "Forgiven", "Forgiving",
    "Freakin' awesome", "Frenetic", "Frenzied", "Friendly", "Amorous", "From a distance", "Frosted", "Funny", "Furry",
    "Galloping", "Gaping", "Gentle", "Giddy", "Glacial", "Gladness", "Gleaming", "Gleeful", "Gorgeous", "Graceful",
    "Grateful", "Halting", "Happy", "Haunting", "Heavenly", "Hidden", "High-spirited", "Honor", "Hopeful", "Hopping",
    "Humble", "Hushed", "Hypnotic", "Illuminated", "Immense", "Imperious", "Impudent", "In charge", "Inflated", "Innocent",
    "Inspired", "Intimate", "Intrepid", "Jagged", "Joking", "Joyful", "Jubilant", "Kindly", "Languid", "Larger than life",
    "Laughable", "Lickety-split", "Lighthearted", "Limping", "Linear", "Lively", "Lofty", "Love of", "Lovely", "Lulling",
    "Luminescent", "Lush", "Luxurious", "Magical", "Maniacal", "Manliness", "March-like", "Masterful", "Merciful", "Merry",
    "Mischievous", "Misty", "Modest", "Moonlit", "Mysterious", "Mystical", "Mythological", "Nebulous", "Nostalgic", "Onfire",
     "Overstated", "Paganish", "Partying", "Perfunctory", "Perky", "Perplexed", "Persevering", "Pious", "Playful",
    "Pleasurable", "Poignant", "Portentous", "Posh", "Powerful", "Pretty", "Prickly", "Prideful", "Princesslike", "Proud",
    "Puzzled", "Queenly", "Questing", "Quiet", "Racy", "Ragged", "Regal", "Rejoicing", "Relaxed", "Reminiscent",
    "Repentant", "Reserved", "Resolute", "Ridiculous", "Ritualistic", "Robust", "Running", "Sarcastic", "Scampering",
    "Scoffing", "Scurrying", "Sensitive", "Serene", "Shaking", "Shining", "Silky", "Silly", "Simple", "Singing", "Skipping",
    "Smooth", "Sneaky", "Soaring", "Sophisticated", "Sparkling", "Spell-like", "Spherical", "Spidery", "Splashing",
    "Splendid", "Spooky", "Sprinting", "Starlit", "Starry", "Startling", "Successful", "Summery", "Surprised",
    "Sympathetic", "Tapping", "Teasing", "Tender", "Thoughtful", "Thrilling", "Tingling", "Tinkling", "Tongue-in-cheek",
    "Totemic", "Touching", "Tranquil", "Treasured", "Trembling", "Triumphant", "Twinkling", "Undulating", "Unruly",
    "Urgent", "Veiled", "Velvety", "Victorious", "Vigorous", "Virile", "Walking", "Wild", "Witty", "Wondering", "Zealous",
    "Zestful"
];

function randItem(list){
    return list[rand(list.length)];
}

function rand(max){
    return Math.floor(Math.random() * Math.floor(max))
}

function randomVet(specialties) {
    const first = randItem(firstNames);
    const last = randItem(adj);
    const numSpec = rand(specialties.length);
    const spec = [...Array(numSpec).keys()].map(n => randItem(specialties))
    return {
        firstName: first,
        lastName: last,
        specialties: spec
    };
}

function randomOwner(){
    const firstName = randItem(firstNames);
    const lastName = randItem(surnames);
    return {
     "firstName": firstName,
     "lastName": lastName,
     "address": `${rand(10000)} ${randItem(adj)} Ln.`,
     "city": "Anytown",
     "telephone": "8005551212",
     "pets": []
   };
}

function randomPet(types, owner) {
    const birthDate = "2020/12/31";
    const petName = ((rand(100) > 50) ? "" : `${randItem(adj)} `) + randItem(petNames);
    const typeId = randItem(types).id;

    return {
     "birthDate": birthDate,
     "id": 0, // one will be chosen for us
     "name": petName,
     "owner": {
       "id": owner.id,
       "firstName": null,
       "lastName": "",
       "address": "",
       "city": "",
       "telephone": ""
     },
     "type": {
       "id": typeId
     },
     "visits": []
   }
 }

export default {
 randomVet,
 randomOwner,
 randomPet
};