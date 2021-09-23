import http from "k6/http";
import {check} from "k6";
import names from "./names.js";

const baseUri = `http://petclinic:9966/petclinic/api`;

export default function() {
    const specialtiesUrl = `${baseUri}/specialties`;
    const specialtiesResponse = http.get(specialtiesUrl);
    const specialties = JSON.parse(specialtiesResponse.body);

    // Add a new vet to the list
    const newVet = names.randomVet(specialties);
    const response = http.post(`${baseUri}/vets`, JSON.stringify(newVet),
            { headers: { 'Content-Type': 'application/json' } });
    // we don't guard against dupes, so this could fail on occasion
    check(response, { "create vet status 201": (r) => r.status === 201 });

    // make sure we can fetch that vet back out
    const vetId = JSON.parse(response.body).id;
    const vetUrl = `${baseUri}/vets/${vetId}`
    const vetResponse = http.get(vetUrl);
    check(vetResponse, { "fetch vet status 200": r => r.status === 200 });

    // add a new owner
    const newOwner = names.randomOwner();
    const newOwnerResponse = http.post(`${baseUri}/owners`, JSON.stringify(newOwner),
          { headers: { 'Content-Type': 'application/json' } });
    check(newOwnerResponse, { "new owner status 201": r => r.status === 201});

    // make sure we can fetch that owner back out
    const ownerId = JSON.parse(newOwnerResponse.body).id;
    const ownerResponse = http.get(`${baseUri}/owners/${ownerId}`);
    check(ownerResponse, { "fetch new owner status 200": r => r.status === 200});
    const owner = JSON.parse(ownerResponse.body);

    // get the list of all pet types
    const petTypes = JSON.parse(http.get(`${baseUri}/pettypes`).body);
    const owners = JSON.parse(http.get(`${baseUri}/owners`).body);

    // create a 3 new random pets
    const pet1 = names.randomPet(petTypes, owner);
    const pet2 = names.randomPet(petTypes, owner);
    const pet3 = names.randomPet(petTypes, owner);

    const petsUrl = `${baseUri}/pets`;
    const newPetResponses = http.batch([
        ["POST", petsUrl, JSON.stringify(pet1), { headers: { 'Content-Type': 'application/json' } } ],
        ["POST", petsUrl, JSON.stringify(pet2), { headers: { 'Content-Type': 'application/json' } } ],
        ["POST", petsUrl, JSON.stringify(pet3), { headers: { 'Content-Type': 'application/json' } } ],
    ]);
    check(newPetResponses[0], { "pet status 201": r => r.status === 201});
    check(newPetResponses[1], { "pet status 201": r => r.status === 201});
    check(newPetResponses[2], { "pet status 201": r => r.status === 201});

    const responses = http.batch([
        ["GET", `${baseUri}/pets/${JSON.parse(newPetResponses[0].body).id}`],
        ["GET", `${baseUri}/pets/${JSON.parse(newPetResponses[1].body).id}`],
        ["GET", `${baseUri}/pets/${JSON.parse(newPetResponses[2].body).id}`]
    ]);
    check(responses[0], { "pet exists 200": r => r.status === 200});
    check(responses[1], { "pet exists 200": r => r.status === 200});
    check(responses[2], { "pet exists 200": r => r.status === 200});

    // Clean up after ourselves.
    // Delete pets
    const petDeletes = http.batch([
      ["DELETE", `${baseUri}/pets/${JSON.parse(newPetResponses[0].body).id}`],
      ["DELETE", `${baseUri}/pets/${JSON.parse(newPetResponses[1].body).id}`],
      ["DELETE", `${baseUri}/pets/${JSON.parse(newPetResponses[2].body).id}`]
    ]);

    check(petDeletes[0], { "pet deleted 204": r => r.status === 204});
    check(petDeletes[1], { "pet deleted 204": r => r.status === 204});
    check(petDeletes[2], { "pet deleted 204": r => r.status === 204});

    // Delete owner
    const delOwner = http.del(`${baseUri}/owners/${ownerId}`);
    check(delOwner, { "owner deleted 204": r => r.status === 204});

    // Delete vet
    const delVet = http.del(`${baseUri}/vets/${vetId}`);
    check(delVet, { "owner deleted 204": r => r.status === 204});

    //TODO: Set up a visit or two
    //TODO: Fetch out the owner again because their model has been updated.

};