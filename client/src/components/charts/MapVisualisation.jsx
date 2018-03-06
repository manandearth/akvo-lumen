import React, { Component } from 'react';
import { render } from 'react-dom';
import PropTypes from 'prop-types';
import leaflet from 'leaflet';
import { isEqual, cloneDeep } from 'lodash';
import leafletUtfGrid from '../../vendor/leaflet.utfgrid';
import Spinner from '../common/LoadingSpinner';
import Legend from './mapVisualization/Legend';
import PopupContent from './mapVisualization/PopupContent';

require('../../../node_modules/leaflet/dist/leaflet.css');
require('./MapVisualisation.scss');

const L = leafletUtfGrid(leaflet);

// We need to use leaflet private methods which have underscore dangle
/* eslint no-underscore-dangle: "off" */

const getBaseLayerAttributes = ((baseLayer) => {
  const attributes = {};

  switch (baseLayer) {
    case 'street':
      attributes.tileUrl = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
      attributes.tileAttribution = '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors';
      break;

    case 'satellite':
      attributes.tileUrl = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
      attributes.tileAttribution = 'Esri, DigitalGlobe, GeoEye, Earthstar Geographics, CNES/Airbus DS, USDA, USGS, AeroGRID, IGN, and the GIS User Community';
      break;

    case 'terrain':
      attributes.tileUrl = 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png';
      attributes.tileAttribution = 'Map data: &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>, <a href="http://viewfinderpanoramas.org">SRTM</a> | Map style: &copy; <a href="https://opentopomap.org">OpenTopoMap</a> (<a href="https://creativecommons.org/licenses/by-sa/3.0/">CC-BY-SA</a>)';
      break;

    default:
      throw new Error(`Unknown base layer type ${baseLayer}`);
  }

  return attributes;
});

export default class MapVisualisation extends Component {

  constructor() {
    super();
    this.renderLeafletLayer = this.renderLeafletLayer.bind(this);
    this.renderLeafletMap = this.renderLeafletMap.bind(this);
  }
  componentDidMount() {
    this.renderLeafletMap(this.props);
  }
  componentWillReceiveProps(nextProps) {
    this.renderLeafletMap(nextProps);
  }
  renderLeafletLayer(layer, id, layerGroupId, layerMetadata, baseURL, map) {
    if (!this[`storedSpec${id}`]) {
      // Store a copy of the layer spec to compare to future changes so we know when to re-render
      this[`storedSpec${id}`] = cloneDeep(layer);
    }

    const newSpec = layer || {};
    const oldSpec = this[`storedSpec${id}`] || {};
    const filtersChanged = !isEqual(newSpec.filters, oldSpec.filters);
    const popup = newSpec.popup;
    const haveAggregation = layer.aggregationGeomColumn;
    const havePopupData = Boolean(popup && popup.length > 0) || haveAggregation;
    const haveUtfGrid = Boolean(this[`utfGrid${id}`]);
    const needToRemovePopup = this[`utfGrid${id}`] && !havePopupData;
    const aggregationChanged = Boolean(
        newSpec.aggregationDataset !== oldSpec.aggregationDataset ||
        newSpec.aggregationColumn !== oldSpec.aggregationColumn ||
        newSpec.aggregationGeomColumn !== oldSpec.aggregationGeomColumn ||
        newSpec.aggregationMethod !== oldSpec.aggregationMethod
    );
    const popupChanged = Boolean(!this[`popup${id}`] || !isEqual(popup, this[`popup${id}`])) || aggregationChanged;
    const needToAddOrUpdate =
      havePopupData && (popupChanged || filtersChanged);
    const windshaftAvailable = Boolean(layerGroupId);
    const canUpdate = windshaftAvailable || needToRemovePopup;

    if ((needToAddOrUpdate || needToRemovePopup) && canUpdate) {
      if (haveUtfGrid) {
        // Remove the existing grid
        this.map.closePopup();
        map.removeLayer(this[`utfGrid${id}`]);
        this[`utfGrid${id}`] = null;
        this[`popup${id}`] = null;
      }

      if (!havePopupData) {
        return;
      }

      this[`popup${id}`] = cloneDeep(popup);
      this[`utfGrid${id}`] =
        // eslint-disable-next-line new-cap
        new L.utfGrid(`${baseURL}/${layerGroupId}/${id}/{z}/{x}/{y}.grid.json?callback={cb}`, {
          resolution: 4,
        });

      this[`utfGrid${id}`].on('click', (e) => {
        if (e.data) {
          this.popupElement = L.popup()
          .setLatLng(e.latlng)
          .openOn(map);

          // Adjust size of popup and map position to make popup contents visible
          const adjustLayoutForPopup = () => {
            this.popupElement.update();
            if (this.popupElement._map && this.popupElement._map._panAnim) {
              this.popupElement._map._panAnim = undefined;
            }
            this.popupElement._adjustPan();
          };

          // Although we use leaflet to create the popup, we can still render the contents
          // with react-dom
          render(
            <PopupContent
              data={e.data}
              singleMetadata={layerMetadata[id]}
              onImageLoad={adjustLayoutForPopup}
            />,
            this.popupElement._contentNode,
            adjustLayoutForPopup
          );
        }
      });
      map.addLayer(this[`utfGrid${id}`]);
    }
  }
  renderLeafletMap(nextProps) {
    const { visualisation, metadata, width, height } = nextProps;
    const { tileUrl, tileAttribution } = getBaseLayerAttributes(visualisation.spec.baseLayer);

    // Windshaft map
    // const tenantDB = visualisation.tenantDB;
    const baseURL = '/maps/layergroup';
    const layerGroupId = metadata.layerGroupId;
    const xCenter = [0, 0];
    const xZoom = 2;

    const node = this.leafletMapNode;

    let map;

    /* General map stuff - not layer specific */
    if (!this.storedBaseLayer) {
      // Do the same thing for the baselayer
      this.storedBaseLayer = cloneDeep(this.props.visualisation.spec.baseLayer);
    }

    if (!this.map) {
      map = L.map(node).setView(xCenter, xZoom);
      map.scrollWheelZoom.disable();
      this.map = map;
    } else {
      map = this.map;
    }

    const haveDimensions = Boolean(this.oldHeight && this.oldWidth);
    const dimensionsChanged = Boolean(height !== this.oldHeight || width !== this.oldWidth);

    if (!haveDimensions || dimensionsChanged) {
      this.map.invalidateSize();
      this.oldHeight = height;
      this.oldWidth = width;
    }

    // Display or update the baselayer tiles
    if (!this.baseLayer) {
      this.baseLayer = L.tileLayer(tileUrl, { attribution: tileAttribution });
      this.baseLayer.addTo(map);
    } else {
      const oldTileUrl = getBaseLayerAttributes(this.storedBaseLayer).tileUrl;
      const newTileUrl = tileUrl;

      if (oldTileUrl !== newTileUrl) {
        this.storedBaseLayer = cloneDeep(nextProps.visualisation.spec.baseLayer);

        map.removeLayer(this.baseLayer);
        this.baseLayer = L.tileLayer(tileUrl, { attribution: tileAttribution });
        this.baseLayer.addTo(map).bringToBack();
      }
    }

    if (metadata && metadata.layerMetadata && metadata.layerMetadata.length) {
      const mergedBoundingBox = [[90, 180], [-90, -180]];

      metadata.layerMetadata.forEach((layer) => {
        if (layer.boundingBox) {
          if (layer.boundingBox[0][0] < mergedBoundingBox[0][0]) {
            mergedBoundingBox[0][0] = layer.boundingBox[0][0];
          }
          if (layer.boundingBox[0][1] < mergedBoundingBox[0][1]) {
            mergedBoundingBox[0][1] = layer.boundingBox[0][1];
          }
          if (layer.boundingBox[1][0] > mergedBoundingBox[1][0]) {
            mergedBoundingBox[1][0] = layer.boundingBox[1][0];
          }
          if (layer.boundingBox[1][1] > mergedBoundingBox[1][1]) {
            mergedBoundingBox[1][1] = layer.boundingBox[1][1];
          }
        }
      });

      if (!isEqual(mergedBoundingBox, this.storedBoundingBox)) {
        this.storedBoundingBox = mergedBoundingBox;
        map.fitBounds(mergedBoundingBox, { maxZoom: 12, minZoom: 1 });
      }
    }

    if (!this.storedSpec) {
      // Store a copy of the layer spec to compare to future changes so we know when to re-render
      this.storedSpec = cloneDeep(visualisation.spec);
    }

    const newSpec = nextProps.visualisation.spec || {};
    const oldSpec = this.storedSpec || {};

    // Add or update the windshaft tile layer if necessary
    if (newSpec.layers.length === 0 && this.dataLayer) {
      map.removeLayer(this.dataLayer);
      this.dataLayer = null;
    } else if (layerGroupId) {
      if (!this.dataLayer) {
        this.dataLayer = L.tileLayer(`${baseURL}/${layerGroupId}/all/{z}/{x}/{y}.png`);
        this.dataLayer.addTo(map);
      } else {
        const needToUpdate = Boolean(
          !isEqual(newSpec.layers, oldSpec.layers)
        );
        if (needToUpdate) {
          this.storedSpec = cloneDeep(this.props.visualisation.spec);

          map.removeLayer(this.dataLayer);
          this.dataLayer = L.tileLayer(`${baseURL}/${layerGroupId}/all/{z}/{x}/{y}.png`);
          this.dataLayer.addTo(map);
        }
      }
    }

    if (layerGroupId !== this.storedLayerGroupId) {
      visualisation.spec.layers.forEach((layer, idx) => {
        this.renderLeafletLayer(
          layer, idx, layerGroupId, metadata.layerMetadata, baseURL, map
        );
      });
    }
    this.storedLayerGroupId = layerGroupId;
  }

  render() {
    const { visualisation, metadata, width, height } = this.props;
    const title = visualisation.name || '';
    const titleLength = title.toString().length;
    const titleHeight = titleLength > 48 ? 56 : 36;
    const mapWidth = width || '100%';
    const mapHeight = height ? height - titleHeight : `calc(100% - ${titleHeight}px)`;
    const needLegend = Boolean(
      visualisation.spec.layers &&
      visualisation.spec.layers.filter(l => l.legend.visible).length &&
      metadata &&
      metadata.layerMetadata &&
      metadata.layerMetadata.length
    );

    return (
      <div
        className="MapVisualisation dashChart"
        style={{
          width,
          height,
        }}
      >
        <h2
          style={{
            height: titleHeight,
            lineHeight: titleLength > 96 ? '16px' : '20px',
            fontSize: titleLength > 96 ? '14px' : '16px',
          }}
        >
          <span>
            {visualisation.name}
          </span>
        </h2>
        <div
          className="mapContainer"
          style={{
            height: mapHeight,
            width: mapWidth,
          }}
        >
          <div
            className="leafletMap"
            ref={(ref) => { this.leafletMapNode = ref; }}
          />
          {needLegend &&
            <Legend
              layers={visualisation.spec.layers}
              layerMetadata={metadata.layerMetadata}
            />
          }
          {
            visualisation.awaitingResponse &&
            <Spinner />
          }
          {
            visualisation.failedToLoad &&
            <div className="failedIndicator" />
          }
        </div>
      </div>
    );
  }
}

MapVisualisation.propTypes = {
  visualisation: PropTypes.object.isRequired,
  metadata: PropTypes.object,
  width: PropTypes.number,
  height: PropTypes.number,
};
