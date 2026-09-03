package dev.jacobandersen.bastion.micropub.type.resp

interface CommonError {
    val error: String
    val errorDescription: String
}