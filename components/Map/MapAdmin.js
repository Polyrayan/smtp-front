import React from "react";
import MapView from 'react-native-maps'
import { UrlTile} from 'react-native-maps'
import {Text, View, FlatList, ListView, StyleSheet,PermissionsAndroid} from "react-native";
import TruckMarker from './TruckMarker';
import ConnectionToServer from '../Connection/ConnectionToServer';
import * as Location from 'expo-location';
import * as Permissions from 'expo-permissions';
import io from "socket.io-client";

export default class MapAdmin extends React.Component {
  constructor(props) {
      super(props);
      this.handleConnection = this.handleConnection.bind(this);
      this.handleCoordinates = this.handleCoordinates.bind(this);
      this.componentDidMount =this.componentDidMount.bind(this);
      this.state = {
        myPos : {
          latitude : -1,
          longitude : -1
        },
        users: [],
      };
  }

  async componentDidMount(){
    const socket = await io("https://smtp-pi.herokuapp.com/")
    await socket.on("chantier/user/connected", this.handleConnection);
    await socket.on("chantier/user/sentCoordinates", this.handleCoordinates);
    await socket.emit("chantier/connect", {
          "userId" : 11111,
          "chantierId" : 31,
          "coordinates": {
            "longitude": 43.8333,
            "latitude": 4.35
          }
    });

  }

  getChantier(){

  }

  handleConnection(data){
    console.log(data.userId +" is connected")
    var copy = this.state.users.slice();
    copy.push(data.userId);
    console.log("users:" + copy);
    // this.setState({
    //   users : copy
    // });
  }

  handleCoordinates(data){
    console.log("coordianates receve: " + JSON.stringify(data));
    let camData = data.coordinates;
    // copy[data.userId] = data.coordinates;
    // console.log("copy : " + JSON.stringify(copy));
    this.setState({
      myPos : {
        latitude : camData.coordinates.latitude,
        longitude : camData.coordinates.latitude
      }
    })
  }

  render() {

    console.log(this.state.users);
    return(
      <View style={{flex: 1}}>
        <MapView
          style = {styles.map}
          region={{
            latitude: 43.8333,
            longitude: 4.35,
            latitudeDelta: 0.1,
            longitudeDelta: 0.0421,
          }}
        >
          <UrlTile
            urlTemplate={"http://c.tile.openstreetmap.org/{z}/{x}/{y}.png"}
          />

          <TruckMarker coords={this.state.myPos}/>

        </MapView>


      </View>
    )
  }
}


const styles = StyleSheet.create({
  map: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
});